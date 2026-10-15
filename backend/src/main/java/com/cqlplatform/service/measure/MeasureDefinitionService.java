package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.CqlTranslationException;
import com.cqlplatform.exception.ResourceLockedException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.util.EditLock;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.NotificationService;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.SemanticVersionComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.cqlplatform.model.measure.ScoringTypeConstants;
import static com.cqlplatform.model.measure.MeasureStatusConstants.*;
import com.cqlplatform.model.measure.MeasureListQuery;
import com.cqlplatform.model.measure.MeasurePage;
import com.cqlplatform.model.measure.MeasureStatusConstants;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureDefinitionService {

    private final MeasureDefinitionRepository repository;
    private final MeasureAuditRepository auditRepository;
    private final NotificationService notificationService;
    private final CqlTranslationService cqlTranslationService;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;
    private final com.cqlplatform.security.OwnershipVerifier ownershipVerifier;
    private final com.cqlplatform.repository.MeasureScheduleRepository scheduleRepository;
    /** PAT-246: a new version starts with the previous version's test cases. */
    private final com.cqlplatform.repository.TestCaseRepository testCaseRepository;
    /** PAT-249: blockers stop submit / approve; four-eyes refuses the author as approver. */
    private final ApprovalReadinessService readinessService;
    /** PAT-253 */
    private final MeasureSetService measureSetService;
    /** PAT-262: assigned reviewers — a new review round resets their decisions; only they approve / reject. */
    private final com.cqlplatform.repository.MeasureReviewerRepository reviewerRepository;
    /** PAT-263: version-to-version differences. */
    private final MeasureDiffService diffService;

    /** Effective tenant: the caller's, or the default tenant for legacy callers with none. */
    public Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    @Value("${measure.locking.timeout-minutes:30}")
    private int lockTimeoutMinutes;

    @Transactional
    public MeasureDefinition create(MeasureDefinition definition) {
        if (repository.existsByTenantIdAndNameAndVersion(effectiveTenantId(), definition.getName(), definition.getVersion())) {
            throw new IllegalArgumentException(
                    "Measure already exists: " + definition.getName() + " v" + definition.getVersion());
        }

        // PAT-222: the creator IS the owner. Measures created through the UI (and by API
        // clients that omit ownerUsername) used to land ownerless, which meant only admins
        // could ever update / delete them (OwnershipVerifier is fail-closed on a null owner)
        // and the review workflow could not notify anyone (BUG-143). A body-supplied owner is
        // ignored — ownership moves only through the /transfer endpoint.
        String creator = ownershipVerifier.getCurrentUsername();
        if (definition.getOwnerUsername() != null && !creator.equals(definition.getOwnerUsername())) {
            log.warn("Ignoring ownerUsername '{}' supplied on create of measure '{}'; owner is the caller '{}'",
                    definition.getOwnerUsername(), definition.getName(), creator);
        }
        definition.setOwnerUsername(creator);
        if (definition.getCreatedBy() == null || definition.getCreatedBy().isBlank()) {
            definition.setCreatedBy(creator);
        }
        // PAT-222: every measure starts its lifecycle as a draft. The review workflow
        // (submit-for-review → approve) is the only way to reach `active`, which is what the
        // PAT-219 evaluation guard keys on — accepting `status` from the body would let a
        // caller skip review entirely. Imported FHIR Measures are no exception: an external
        // "active" is not this deployment's approval.
        if (definition.getStatus() != null && !DRAFT.equals(definition.getStatus())) {
            log.info("Ignoring status '{}' supplied on create of measure '{}'; measures always start as draft",
                    definition.getStatus(), definition.getName());
        }
        definition.setStatus(DRAFT);
        MeasureMetadataRules.requireOrderedEffectivePeriod(definition.getEffectiveStart(), definition.getEffectiveEnd());
        MeasureMetadataRules.requireOrderedMeasurementPeriod(definition.getMeasurementPeriodStart(), definition.getMeasurementPeriodEnd());
        MeasureMetadataRules.requireOrderedMeasurementPeriod(definition.getMeasurementPeriodStart(), definition.getMeasurementPeriodEnd());

        validateComposite(definition, null);   // PAT-259

        MeasureDefinitionEntity entity = modelToEntity(definition);
        // PAT-253: a brand-new measure opens its own version lineage (createVersionAs stays in
        // the source's set); a measureSetId supplied in the body is ignored.
        entity.setMeasureSetId(measureSetService.createFor(effectiveTenantId(), definition.getName()));
        // Pre-compile CQL on create, same as update — surfaces translation errors at
        // save time rather than at first evaluation.
        if (definition.getCqlContent() != null && !definition.getCqlContent().isBlank()) {
            entity.setElmJson(preCompileElm(definition.getCqlContent()));
        }
        entity = repository.save(entity);
        log.info("Created measure definition: {} v{}", entity.getName(), entity.getVersion());
        recordAudit(entity.getId(), "CREATE", entity.getCreatedBy(), "Created " + entity.getName() + " v" + entity.getVersion(), null, null);
        return entityToModel(entity);
    }

    /**
     * Update a measure's content and metadata.
     *
     * @param currentUser the authenticated caller (from the controller). Used for the lock
     *                    check and the audit row — it used to be smuggled in through the
     *                    body's {@code ownerUsername}, which is wrong for a shared editor
     *                    (PAT-222).
     */
    @Transactional
    public MeasureDefinition update(Long id, MeasureDefinition definition, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        // Only the lock holder can save while the edit lock is active (PAT-253: 409 Locked,
        // expired lock = no lock — the one rule shared with test cases and CQL libraries)
        requireNotLockedByOther(entity, currentUser);

        // PAT-222: lifecycle status is NOT editable here. Until now a PUT could flip a draft
        // straight to `active` (or an active measure back to draft) without submit-for-review /
        // approve / reject / retire — which made the review workflow, and the PAT-219
        // evaluation guard that trusts `active`, bypassable by anyone allowed to edit.
        if (definition.getStatus() != null && !definition.getStatus().equals(entity.getStatus())) {
            throw new ValidationException(
                    "Measure status cannot be changed through update (current: '" + entity.getStatus()
                            + "', requested: '" + definition.getStatus() + "'). Use the review workflow: "
                            + "submit-for-review, approve, reject or retire.");
        }

        MeasureMetadataRules.requireOrderedEffectivePeriod(definition.getEffectiveStart(), definition.getEffectiveEnd());
        MeasureMetadataRules.requireOrderedMeasurementPeriod(definition.getMeasurementPeriodStart(), definition.getMeasurementPeriodEnd());
        MeasureMetadataRules.requireOrderedMeasurementPeriod(definition.getMeasurementPeriodStart(), definition.getMeasurementPeriodEnd());

        // BUG-147: approved / in-review / retired logic is immutable. PAT-222 stopped a PUT from
        // flipping the status, but a PUT could still rewrite the CQL of an approved measure — the
        // evaluation guard trusts `active`, so that was unreviewed logic running as approved.
        // Descriptive metadata stays editable; logic changes go into a new (draft) version.
        if (!DRAFT.equals(entity.getStatus()) && MeasureLogic.changed(entity, definition.getCqlContent(),
                definition.getGroupDefinitions(), definition.getScoringType(), definition.getCompositeScoring(),
                definition.getComponentMeasureIds(), definition.getCqlLibraryId(), definition.getValueSetVersions())) {
            throw new com.cqlplatform.exception.MeasureLogicLockedException(id, entity.getStatus());
        }

        validateComposite(definition, id);   // PAT-259

        if (!java.util.Objects.equals(entity.getName(), definition.getName())) {
            // PAT-253: the lineage is the set, not the name — a rename keeps the versions together
            measureSetService.rename(entity.getMeasureSetId(), entity.getTenantId(), definition.getName());
        }
        entity.setName(definition.getName());
        entity.setVersion(definition.getVersion());
        entity.setTitle(definition.getTitle());
        entity.setDescription(definition.getDescription());
        entity.setScoringType(definition.getScoringType());
        entity.setCqlLibraryId(definition.getCqlLibraryId());
        boolean cqlChanged = !java.util.Objects.equals(definition.getCqlContent(), entity.getCqlContent());
        entity.setCqlContent(definition.getCqlContent());
        if (cqlChanged) {
            entity.setElmJson(preCompileElm(definition.getCqlContent()));
        }
        entity.setFhirMeasureJson(definition.getFhirMeasureJson());
        entity.setGroupDefinitionList(definition.getGroupDefinitions());
        entity.setCompositeScoring(definition.getCompositeScoring());
        entity.setComponentMeasureIdList(definition.getComponentMeasureIds());
        entity.applyComponentWeights(weightMap(definition.getComponentWeights()));   // PAT-259 (writes the column too)
        entity.applyValueSetVersions(pinMap(definition.getValueSetVersions()));      // PAT-260
        entity.setSetting(definition.getSetting());

        // Enhanced metadata
        entity.setRationale(definition.getRationale());
        entity.setClinicalGuidance(definition.getClinicalGuidance());
        entity.setSteward(definition.getSteward());
        entity.setDeveloperList(definition.getDevelopers());
        entity.setReferenceList(definition.getReferences());
        entity.setDisclaimer(definition.getDisclaimer());
        entity.setCopyright(definition.getCopyright());
        entity.setMeasureSet(definition.getMeasureSet());
        entity.setNqfNumber(definition.getNqfNumber());
        entity.setCmsMeasureId(definition.getCmsMeasureId());
        entity.setEcqmTitle(definition.getEcqmTitle());           // PAT-256
        entity.setEndorser(definition.getEndorser());
        entity.setEndorsementId(definition.getEndorsementId());
        entity.setSupplementalDataGuidance(definition.getSupplementalDataGuidance());
        entity.setRiskAdjustmentDescription(definition.getRiskAdjustmentDescription());
        entity.setRiskAdjustmentList(definition.getRiskAdjustments());
        entity.setSupplementalDataList(definition.getSupplementalData());
        entity.setImprovementNotation(definition.getImprovementNotation());
        entity.setRateAggregation(definition.getRateAggregation());
        // PAT-236 standard metadata
        entity.setMeasureTypeList(definition.getMeasureTypes() != null ? definition.getMeasureTypes() : new java.util.ArrayList<>());
        entity.setDefinitionTermList(definition.getDefinitionTerms() != null ? definition.getDefinitionTerms() : new java.util.ArrayList<>());
        entity.setClinicalRecommendationStatement(definition.getClinicalRecommendationStatement());
        entity.setEffectiveStart(definition.getEffectiveStart());
        entity.setEffectiveEnd(definition.getEffectiveEnd());
        entity.setApprovalDate(definition.getApprovalDate());
        entity.setLastReviewDate(definition.getLastReviewDate());
        entity.setExperimental(definition.getExperimental());
        entity.setMeasurementPeriodStart(definition.getMeasurementPeriodStart());
        entity.setMeasurementPeriodEnd(definition.getMeasurementPeriodEnd());
        entity.setMeasurementPeriodStart(definition.getMeasurementPeriodStart());
        entity.setMeasurementPeriodEnd(definition.getMeasurementPeriodEnd());

        // Indicator code mapping
        entity.setMohIndicatorCode(definition.getMohIndicatorCode());
        entity.setNhiaP4pCode(definition.getNhiaP4pCode());
        entity.setDrgIndicatorCode(definition.getDrgIndicatorCode());
        entity.setIndicatorCategory(definition.getIndicatorCategory());
        entity.setDepartment(definition.getDepartment());

        // Sharing fields from update. PAT-222: ownerUsername is deliberately NOT taken from
        // the body — that was a second way to transfer ownership without the /transfer
        // endpoint's checks and notification. The UI sends the whole object back, so a
        // matching owner is the normal case; anything else is logged and dropped.
        if (definition.getOwnerUsername() != null
                && !definition.getOwnerUsername().equals(entity.getOwnerUsername())) {
            log.warn("Ignoring ownerUsername change '{}' -> '{}' on update of measure {} by '{}'; use /transfer",
                    entity.getOwnerUsername(), definition.getOwnerUsername(), id, currentUser);
        }
        if (definition.getSharedWith() != null) {
            entity.setSharedWithList(definition.getSharedWith());
        }
        if (definition.getAccessLevel() != null) {
            entity.setAccessLevel(definition.getAccessLevel());
        }

        entity = repository.save(entity);
        log.info("Updated measure definition: {} v{}", entity.getName(), entity.getVersion());
        recordAudit(entity.getId(), "UPDATE", currentUser, "Updated " + entity.getName() + " v" + entity.getVersion(), null, null);
        return entityToModel(entity);
    }

    @Transactional(readOnly = true)
    public Optional<MeasureDefinition> getById(Long id) {
        return repository.findByIdAndTenantId(id, effectiveTenantId()).map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public Optional<MeasureDefinition> getByNameAndVersion(String name, String version) {
        return repository.findByTenantIdAndNameAndVersion(effectiveTenantId(), name, version).map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getAll() {
        return repository.findByTenantId(effectiveTenantId()).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> search(String searchTerm) {
        return search(searchTerm, null);
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> search(String searchTerm, String department) {
        boolean hasSearch = searchTerm != null && !searchTerm.isBlank();
        boolean hasDept = department != null && !department.isBlank();

        Long tenantId = effectiveTenantId();
        List<MeasureDefinitionEntity> entities;
        if (hasSearch && hasDept) {
            entities = repository.findByTenantIdAndDepartmentAndSearchTerm(tenantId, department, InputValidator.escapeLikeWildcards(searchTerm));
        } else if (hasDept) {
            entities = repository.findByTenantIdAndDepartment(tenantId, department);
        } else if (hasSearch) {
            entities = repository.searchByTenant(tenantId, InputValidator.escapeLikeWildcards(searchTerm));
        } else {
            entities = repository.findByTenantId(tenantId);
        }
        return entities.stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    /** PAT-257: sortable properties of the list page (entity property names). */
    static final Map<String, String> LIST_SORT_FIELDS = Map.of(
            "name", "name", "title", "title", "version", "version", "status", "status", "scoringType", "scoringType",
            "department", "department", "ownerUsername", "ownerUsername", "createdAt", "createdAt", "updatedAt", "updatedAt");
    private static final java.util.Set<String> LIST_STATUSES = java.util.Set.of(
            MeasureStatusConstants.DRAFT, MeasureStatusConstants.IN_REVIEW, MeasureStatusConstants.ACTIVE, MeasureStatusConstants.RETIRED);

    /**
     * PAT-257: one page of the tenant's measures, filtered and sorted on the server. Statuses are validated
     * against the lifecycle states and the sort field against {@link #LIST_SORT_FIELDS}; the id is the
     * tie-breaker so paging stays stable when the sort key repeats.
     */
    @Transactional(readOnly = true)
    public MeasurePage listPage(MeasureListQuery query, int page, int size, String sortField, boolean descending) {
        String property = LIST_SORT_FIELDS.get(sortField == null || sortField.isBlank() ? "updatedAt" : sortField);
        if (property == null) throw new ValidationException("Unsupported sort field: " + sortField);
        if (query.statuses() != null) {
            for (String s : query.statuses()) {
                if (!LIST_STATUSES.contains(s)) throw new ValidationException("Unknown status: " + s);
            }
        }
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort
                .by(descending ? org.springframework.data.domain.Sort.Direction.DESC : org.springframework.data.domain.Sort.Direction.ASC, property)
                .and(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"));
        org.springframework.data.domain.Page<MeasureDefinitionEntity> result = repository.findAll(
                MeasureSpecifications.list(effectiveTenantId(), query),
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200), sort));
        return MeasurePage.builder()
                .content(result.getContent().stream().map(this::entityToModel).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    // ===== PAT-260: terminology pins =====

    private static Map<String, String> pinMap(Map<String, String> pins) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (pins != null) {
            pins.forEach((url, version) -> {
                if (url != null && !url.isBlank() && version != null && !version.isBlank()) out.put(url.trim(), version.trim());
            });
        }
        return out;
    }

    /**
     * Replaces the terminology pins (value set URL → version). Pins are measure logic: outside draft a change
     * is refused like any other logic edit (BUG-147, 409 Measure Logic Locked); the edit lock applies.
     */
    @Transactional
    public MeasureDefinition updateValueSetVersions(Long id, Map<String, String> pins, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        requireNotLockedByOther(entity, currentUser);
        Map<String, String> next = pinMap(pins);
        Map<String, String> current = entity.getValueSetVersionMap() == null ? Map.of() : entity.getValueSetVersionMap();
        if (!DRAFT.equals(entity.getStatus()) && !current.equals(next)) {
            throw new com.cqlplatform.exception.MeasureLogicLockedException(id, entity.getStatus());
        }
        entity.applyValueSetVersions(next);   // sets the column too — a pin-only change must still be written
        entity = repository.save(entity);
        recordAudit(id, "PIN_TERMINOLOGY", currentUser,
                next.isEmpty() ? "Cleared terminology pins" : "Pinned " + next.size() + " value set version(s): " + next, null, null);
        return entityToModel(entity);
    }

    // ===== PAT-259: composite measures =====

    /**
     * A composite names at least two existing, non-composite components of this tenant, never itself,
     * each at most once; its scoring method is one of the four QM IG methods (blank = opportunity,
     * normalised to lower case); weights, when given, key on a component and are positive numbers.
     */
    void validateComposite(MeasureDefinition definition, Long selfId) {
        if (!ScoringTypeConstants.COMPOSITE.equalsIgnoreCase(definition.getScoringType())) return;
        String raw = definition.getCompositeScoring();
        String method = raw == null || raw.isBlank() ? ScoringTypeConstants.COMPOSITE_OPPORTUNITY : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ScoringTypeConstants.COMPOSITE_SCORING_METHODS.contains(method)) {
            throw new ValidationException("Unknown composite scoring method '" + raw + "' (expected opportunity, all-or-nothing, linear or weighted)");
        }
        definition.setCompositeScoring(method);
        List<Long> ids = definition.getComponentMeasureIds() == null ? List.of() : definition.getComponentMeasureIds();
        if (ids.size() < 2) {
            throw new ValidationException("A composite measure needs at least two component measures");
        }
        if (new java.util.HashSet<>(ids).size() != ids.size()) {
            throw new ValidationException("A component measure is listed more than once");
        }
        if (selfId != null && ids.contains(selfId)) {
            throw new ValidationException("A composite measure cannot be its own component");
        }
        Map<Long, MeasureDefinitionEntity> found = new java.util.HashMap<>();
        for (MeasureDefinitionEntity c : repository.findByTenantIdAndIdIn(effectiveTenantId(), ids)) found.put(c.getId(), c);
        for (Long id : ids) {
            MeasureDefinitionEntity c = found.get(id);
            if (c == null) throw new ValidationException("Component measure " + id + " was not found");
            if (ScoringTypeConstants.COMPOSITE.equalsIgnoreCase(c.getScoringType())) {
                throw new ValidationException("Component measure " + id + " (" + c.getName() + ") is itself a composite; composites cannot be nested");
            }
        }
        if (definition.getComponentWeights() != null) {
            for (Map.Entry<String, Double> w : definition.getComponentWeights().entrySet()) {
                Long key;
                try {
                    key = Long.valueOf(w.getKey());
                } catch (NumberFormatException e) {
                    throw new ValidationException("Component weight key '" + w.getKey() + "' is not a measure id");
                }
                if (!ids.contains(key)) throw new ValidationException("Component weight for " + key + " does not match a component");
                if (w.getValue() == null || w.getValue().isNaN() || w.getValue().isInfinite() || w.getValue() <= 0) {
                    throw new ValidationException("Component weight for " + key + " must be a positive number");
                }
            }
        }
    }

    private static Map<Long, Double> weightMap(Map<String, Double> weights) {
        Map<Long, Double> out = new java.util.LinkedHashMap<>();
        if (weights != null) {
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                try {
                    out.put(Long.valueOf(e.getKey()), e.getValue());
                } catch (NumberFormatException ignored) {
                    // validateComposite rejects non-numeric keys before this runs
                }
            }
        }
        return out;
    }

    private static Map<String, Double> weightKeys(Map<Long, Double> weights) {
        if (weights == null || weights.isEmpty()) return null;
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        weights.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    /** The components of a composite resolved to name / version / title / status (+ weight), in declared order; null otherwise. */
    private List<MeasureDefinition.ComponentRef> componentRefs(MeasureDefinitionEntity entity) {
        List<Long> ids = entity.getComponentMeasureIdList();
        if (!ScoringTypeConstants.COMPOSITE.equalsIgnoreCase(entity.getScoringType()) || ids == null || ids.isEmpty()) return null;
        Map<Long, MeasureDefinitionEntity> byId = new java.util.HashMap<>();
        Long tenantId = entity.getTenantId() != null ? entity.getTenantId() : effectiveTenantId();
        List<MeasureDefinitionEntity> components = repository.findByTenantIdAndIdIn(tenantId, ids);
        if (components != null) for (MeasureDefinitionEntity c : components) byId.put(c.getId(), c);
        Map<Long, Double> weights = entity.getComponentWeightMap();
        List<MeasureDefinition.ComponentRef> refs = new ArrayList<>();
        for (Long id : ids) {
            MeasureDefinitionEntity c = byId.get(id);
            refs.add(MeasureDefinition.ComponentRef.builder()
                    .measureId(id)
                    .name(c != null ? c.getName() : null)
                    .version(c != null ? c.getVersion() : null)
                    .title(c != null ? c.getTitle() : null)
                    .status(c != null ? c.getStatus() : null)
                    .weight(weights != null ? weights.get(id) : null)
                    .build());
        }
        return refs;
    }

    @Transactional
    public void delete(Long id) {
        recordAudit(id, "DELETE", null, "Deleted measure " + id, null, null);
        repository.deleteById(id);
        log.info("Deleted measure definition: {}", id);
    }

    // ===== Version Management =====

    @Transactional
    public MeasureDefinition createVersion(Long id, String versionType) {
        MeasureDefinitionEntity existing = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        return createVersionAs(id, bumpVersion(existing.getVersion(), versionType));
    }

    /** The next minor version of {@code version} that this measure name does not use yet in the tenant. */
    @Transactional(readOnly = true)
    public String nextFreeMinorVersion(String name, String version) {
        String candidate = bumpVersion(version, "minor");
        while (repository.existsByTenantIdAndNameAndVersion(effectiveTenantId(), name, candidate)) {
            candidate = bumpVersion(candidate, "minor");
        }
        return candidate;
    }

    /** PAT-253: the next minor version after {@code existing}'s that its measure set does not use yet. */
    @Transactional(readOnly = true)
    public String nextFreeMinorVersion(MeasureDefinitionEntity existing) {
        String candidate = bumpVersion(existing.getVersion(), "minor");
        while (versionTaken(existing, candidate)) {
            candidate = bumpVersion(candidate, "minor");
        }
        return candidate;
    }

    /**
     * PAT-253: whether {@code version} is already used in {@code existing}'s lineage. Version numbers
     * are unique within the measure set, not the name — a renamed version still counts, an unrelated
     * measure that happens to share the name does not. Rows without a set fall back to the name.
     */
    @Transactional(readOnly = true)
    public boolean versionTaken(MeasureDefinitionEntity existing, String version) {
        return existing.getMeasureSetId() != null
                ? repository.existsByMeasureSetIdAndVersion(existing.getMeasureSetId(), version)
                : repository.existsByTenantIdAndNameAndVersion(effectiveTenantId(), existing.getName(), version);
    }

    /**
     * A new draft copy of measure {@code id} with the given version (BUG-147: the eCQM builder
     * publishes changed logic of an approved measure into one of these). Everything is copied —
     * department, indicator codes, sharing, metadata — and the status starts as draft.
     */
    @Transactional
    public MeasureDefinition createVersionAs(Long id, String newVersion) {
        MeasureDefinitionEntity existing = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        if (versionTaken(existing, newVersion)) {
            throw new IllegalArgumentException("Version already exists: " + existing.getName() + " v" + newVersion);
        }

        // PAT-222: the source version keeps whatever lifecycle status it has. This used to
        // force it to `active`, i.e. creating a new version of a draft silently approved the
        // old one without review — a third bypass of the workflow.

        // Create new draft copy
        MeasureDefinitionEntity newEntity = modelToEntity(entityToModel(existing));
        newEntity.setId(null);
        newEntity.setVersion(newVersion);
        newEntity.setStatus(DRAFT);
        newEntity.setTenantId(existing.getTenantId()); // version chain stays in the source tenant
        newEntity.setMeasureSetId(existing.getMeasureSetId()); // PAT-253: same lineage
        newEntity = repository.save(newEntity);

        // PAT-246: the test cases come along (MADiE's "create draft" does the same) — the new
        // version's logic starts identical, so every existing test still applies; run results are
        // reset because they belong to the old version's runs.
        int copiedTestCases = copyTestCases(existing.getId(), newEntity.getId());

        log.info("Created version {} for measure {} ({} test cases copied)", newVersion, existing.getName(), copiedTestCases);
        return entityToModel(newEntity);
    }

    /** PAT-246: copies every test case of {@code fromMeasureId} onto {@code toMeasureId}; returns how many. */
    private int copyTestCases(Long fromMeasureId, Long toMeasureId) {
        List<com.cqlplatform.entity.TestCaseEntity> sources =
                testCaseRepository.findByMeasureDefinitionIdOrderByCreatedAtAsc(fromMeasureId);
        if (sources.isEmpty()) return 0;
        List<com.cqlplatform.entity.TestCaseEntity> copies = new ArrayList<>();
        for (com.cqlplatform.entity.TestCaseEntity source : sources) {
            copies.add(TestCaseCopies.copyOf(source, toMeasureId));
        }
        testCaseRepository.saveAll(copies);
        return copies.size();
    }

    /**
     * PAT-253: every version of the measure's set (its lineage), newest first — a rename no longer
     * drops versions out of the history. Rows without a set (built before V79 outside the migration,
     * e.g. in H2 tests) fall back to the pre-V79 name-based lineage.
     */
    @Transactional(readOnly = true)
    public List<MeasureDefinition> getHistory(MeasureDefinition measure) {
        return lineageOf(effectiveTenantId(), measure.getMeasureSetId(), measure.getName()).stream()
                .sorted(Comparator.comparing(MeasureDefinitionEntity::getVersion, new SemanticVersionComparator()).reversed())
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    private List<MeasureDefinitionEntity> lineageOf(Long tenantId, Long measureSetId, String name) {
        return measureSetId != null
                ? repository.findByTenantIdAndMeasureSetId(tenantId, measureSetId)
                : repository.findByTenantIdAndName(tenantId, name);
    }

    /**
     * PAT-253: applies {@code change} to {@code entity} and to every other version in its set —
     * access (sharing, owner, access level) is a property of the lineage, as in MADiE, so a
     * reviewer shared on v1 can see the v2 draft without a second share. Returns the saved entity.
     */
    private MeasureDefinitionEntity applyAcrossSet(MeasureDefinitionEntity entity,
                                                   java.util.function.Consumer<MeasureDefinitionEntity> change) {
        change.accept(entity);
        MeasureDefinitionEntity saved = repository.save(entity);
        if (entity.getMeasureSetId() != null) {
            for (MeasureDefinitionEntity other : repository.findByTenantIdAndMeasureSetId(entity.getTenantId(), entity.getMeasureSetId())) {
                if (other.getId().equals(entity.getId())) continue;
                change.accept(other);
                repository.save(other);
            }
        }
        return saved;
    }

    /** PAT-263: the structured, human-readable difference between two versions (metadata by section, groups by id, CQL line diff). */
    @Transactional(readOnly = true)
    public com.cqlplatform.model.measure.MeasureVersionDiff compare(Long oldId, Long newId) {
        MeasureDefinition oldMeasure = getById(oldId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + oldId));
        MeasureDefinition newMeasure = getById(newId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + newId));
        return diffService.diff(oldMeasure, newMeasure);
    }

    private String bumpVersion(String version, String type) {
        String[] parts = version.split("\\.");
        int major = parts.length > 0 ? Integer.parseInt(parts[0].trim()) : 0;
        int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;

        switch (type.toLowerCase()) {
            case "major": major++; minor = 0; patch = 0; break;
            case "minor": minor++; patch = 0; break;
            case "patch": patch++; break;
            default: throw new IllegalArgumentException("Invalid version type: " + type);
        }
        return major + "." + minor + "." + patch;
    }

    private MeasureDefinition entityToModel(MeasureDefinitionEntity entity) {
        boolean locked = EditLock.isActive(entity.getLockedBy(), entity.getLockedAt(), lockTimeoutMinutes);
        return MeasureDefinition.builder()
                .id(entity.getId())
                .measureSetId(entity.getMeasureSetId())
                .name(entity.getName())
                .version(entity.getVersion())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .scoringType(entity.getScoringType())
                .cqlLibraryId(entity.getCqlLibraryId())
                .cqlContent(entity.getCqlContent())
                .elmJson(entity.getElmJson())
                .fhirMeasureJson(entity.getFhirMeasureJson())
                .groupDefinitions(entity.getGroupDefinitionList())
                .compositeScoring(entity.getCompositeScoring())
                .componentMeasureIds(entity.getComponentMeasureIdList())
                .componentWeights(weightKeys(entity.getComponentWeightMap()))        // PAT-259
                .valueSetVersions(entity.getValueSetVersionMap() == null || entity.getValueSetVersionMap().isEmpty()
                        ? null : new java.util.LinkedHashMap<>(entity.getValueSetVersionMap()))   // PAT-260
                .componentMeasures(componentRefs(entity))
                .createdBy(entity.getCreatedBy())
                .ownerUsername(entity.getOwnerUsername())
                .sharedWith(entity.getSharedWithList())
                .accessLevel(entity.getAccessLevel())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .lockedBy(locked ? entity.getLockedBy() : null)
                .lockedAt(locked ? entity.getLockedAt() : null)
                .lockExpiresAt(locked ? EditLock.expiresAt(entity.getLockedAt(), lockTimeoutMinutes) : null)
                .reviewedBy(entity.getReviewedBy())
                .approvedBy(entity.getApprovedBy())
                .reviewComment(entity.getReviewComment())
                .reviewedAt(entity.getReviewedAt())
                .setting(entity.getSetting())
                // Enhanced metadata
                .rationale(entity.getRationale())
                .clinicalGuidance(entity.getClinicalGuidance())
                .steward(entity.getSteward())
                .developers(entity.getDeveloperList())
                .references(entity.getReferenceList())
                .disclaimer(entity.getDisclaimer())
                .copyright(entity.getCopyright())
                .measureSet(entity.getMeasureSet())
                .nqfNumber(entity.getNqfNumber())
                .cmsMeasureId(entity.getCmsMeasureId())
                .ecqmTitle(entity.getEcqmTitle())
                .endorser(entity.getEndorser())
                .endorsementId(entity.getEndorsementId())
                .supplementalDataGuidance(entity.getSupplementalDataGuidance())
                .riskAdjustmentDescription(entity.getRiskAdjustmentDescription())
                .riskAdjustments(entity.getRiskAdjustmentList())
                .supplementalData(entity.getSupplementalDataList())
                .improvementNotation(entity.getImprovementNotation())
                .rateAggregation(entity.getRateAggregation())
                // PAT-236 standard metadata
                .measureTypes(entity.getMeasureTypeList())
                .definitionTerms(entity.getDefinitionTermList())
                .clinicalRecommendationStatement(entity.getClinicalRecommendationStatement())
                .effectiveStart(entity.getEffectiveStart())
                .effectiveEnd(entity.getEffectiveEnd())
                .approvalDate(entity.getApprovalDate())
                .lastReviewDate(entity.getLastReviewDate())
                .experimental(entity.getExperimental())
                .measurementPeriodStart(entity.getMeasurementPeriodStart())
                .measurementPeriodEnd(entity.getMeasurementPeriodEnd())
                .measurementPeriodStart(entity.getMeasurementPeriodStart())
                .measurementPeriodEnd(entity.getMeasurementPeriodEnd())
                // Indicator code mapping
                .mohIndicatorCode(entity.getMohIndicatorCode())
                .nhiaP4pCode(entity.getNhiaP4pCode())
                .drgIndicatorCode(entity.getDrgIndicatorCode())
                .indicatorCategory(entity.getIndicatorCategory())
                .department(entity.getDepartment())
                .build();
    }

    // ===== Sharing & Permissions =====

    @Transactional
    public MeasureDefinition shareMeasure(Long id, String targetUsername, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        entity = applyAcrossSet(entity, member -> {
            List<String> shared = new ArrayList<>(member.getSharedWithList());
            if (!shared.contains(targetUsername)) {
                shared.add(targetUsername);
            }
            member.setSharedWithList(shared);
            if ("private".equals(member.getAccessLevel())) {
                member.setAccessLevel("shared");
            }
        });
        recordAudit(id, "SHARE", currentUser, "Shared with " + targetUsername, null, null);
        log.info("Shared measure {} with user {}", id, targetUsername);

        // Notify the target user
        notificationService.notifyMeasureShared(currentUser, targetUsername, entity.getName(), id);

        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition unshareMeasure(Long id, String targetUsername, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        entity = applyAcrossSet(entity, member -> {
            List<String> shared = new ArrayList<>(member.getSharedWithList());
            shared.remove(targetUsername);
            member.setSharedWithList(shared);
            if (shared.isEmpty() && "shared".equals(member.getAccessLevel())) {
                member.setAccessLevel("private");
            }
        });
        recordAudit(id, "UNSHARE", currentUser, "Removed sharing for " + targetUsername, null, null);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition transferOwnership(Long id, String newOwner, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        String oldOwner = entity.getOwnerUsername();
        entity = applyAcrossSet(entity, member -> member.setOwnerUsername(newOwner));
        recordAudit(id, "TRANSFER", currentUser, "Transferred from " + oldOwner + " to " + newOwner, oldOwner, newOwner);
        log.info("Transferred measure {} from {} to {}", id, currentUser, newOwner);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition setAccessLevel(Long id, String accessLevel, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        String oldLevel = entity.getAccessLevel();
        entity = applyAcrossSet(entity, member -> member.setAccessLevel(accessLevel));
        recordAudit(id, "ACCESS_CHANGE", currentUser, "Access level changed", oldLevel, accessLevel);
        return entityToModel(entity);
    }

    // ===== Locking =====

    @Transactional
    public MeasureDefinition lockMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        requireNotLockedByOther(entity, currentUser); // re-locking by the holder refreshes the lock

        entity.setLockedBy(currentUser);
        entity.setLockedAt(java.time.LocalDateTime.now());
        entity = repository.save(entity);
        recordAudit(id, "LOCK", currentUser, "Locked by " + currentUser, null, null);
        log.info("Measure {} locked by {}", id, currentUser);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition unlockMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        if (entity.getLockedBy() == null) {
            return entityToModel(entity);
        }

        // Only the lock holder or the owner can unlock
        boolean isLockHolder = entity.getLockedBy().equals(currentUser);
        boolean isOwner = entity.getOwnerUsername() == null || entity.getOwnerUsername().equals(currentUser);
        if (!isLockHolder && !isOwner) {
            throw new ResourceLockedException("Measure", id, entity.getLockedBy(),
                    EditLock.expiresAt(entity.getLockedAt(), lockTimeoutMinutes),
                    "Measure " + id + " is locked by " + entity.getLockedBy()
                            + "; only the lock holder or the owner can unlock it.");
        }

        String previousHolder = entity.getLockedBy();
        entity.setLockedBy(null);
        entity.setLockedAt(null);
        entity = repository.save(entity);
        recordAudit(id, "UNLOCK", currentUser, "Unlocked (was locked by " + previousHolder + ")", null, null);
        log.info("Measure {} unlocked by {}", id, currentUser);
        return entityToModel(entity);
    }

    /** PAT-253: 409 Locked while someone other than {@code currentUser} holds an active edit lock ({@link EditLock}). */
    private void requireNotLockedByOther(MeasureDefinitionEntity entity, String currentUser) {
        if (EditLock.isHeldByOther(entity.getLockedBy(), entity.getLockedAt(), currentUser, lockTimeoutMinutes)) {
            throw new ResourceLockedException("Measure", entity.getId(), entity.getLockedBy(),
                    EditLock.expiresAt(entity.getLockedAt(), lockTimeoutMinutes));
        }
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getMeasuresByOwner(String ownerUsername) {
        return repository.findByTenantIdAndOwnerUsername(effectiveTenantId(), ownerUsername).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getSharedMeasures(String username) {
        return repository.findSharedWithUser(effectiveTenantId(), "%\"" + InputValidator.escapeLikeWildcards(username) + "\"%").stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getAccessibleMeasures(String username) {
        List<MeasureDefinition> owned = getMeasuresByOwner(username);
        List<MeasureDefinition> shared = getSharedMeasures(username);
        Map<Long, MeasureDefinition> merged = new LinkedHashMap<>();
        owned.forEach(m -> merged.put(m.getId(), m));
        shared.forEach(m -> merged.putIfAbsent(m.getId(), m));
        return new ArrayList<>(merged.values());
    }

    // ===== Workflow =====

    @Transactional
    public MeasureDefinition submitForReview(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        validateTransition(entity.getStatus(), IN_REVIEW);
        readinessService.requireReady(entity, "be submitted for review");

        String oldStatus = entity.getStatus();
        entity.setStatus(IN_REVIEW);
        entity = repository.save(entity);
        recordAudit(id, "SUBMIT_FOR_REVIEW", currentUser, "Submitted for review", oldStatus, IN_REVIEW);
        log.info("Measure {} submitted for review by {}", id, currentUser);

        // PAT-262: a new review round — every assigned reviewer is back to pending
        List<String> assigned = assignedReviewers(entity);
        if (!assigned.isEmpty()) {
            reviewerRepository.resetDecisions(entity.getId(), entity.getTenantId());
        }
        // Notify shared users and assigned reviewers
        List<String> reviewers = new ArrayList<>(entity.getSharedWithList());
        for (String r : assigned) if (!reviewers.contains(r)) reviewers.add(r);
        notificationService.notifyMeasureSubmitted(currentUser, reviewers, entity.getName(), id);

        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition approveMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkReviewer(entity, currentUser);
        validateTransition(entity.getStatus(), ACTIVE);
        readinessService.requireFourEyes(entity, currentUser);
        readinessService.requireReady(entity, "be approved");

        String oldStatus = entity.getStatus();
        entity.setStatus(ACTIVE);
        entity.setApprovedBy(currentUser);
        entity.setReviewedBy(currentUser);
        entity.setReviewedAt(java.time.LocalDateTime.now());
        entity.setReviewComment(null);
        entity = repository.save(entity);
        recordAudit(id, "APPROVE", currentUser, "Approved and set to active", oldStatus, ACTIVE);
        log.info("Measure {} approved by {}", id, currentUser);
        supersedeOtherActiveVersions(entity, currentUser);

        // Notify the measure owner
        notificationService.notifyMeasureApproved(currentUser, entity.getOwnerUsername(), entity.getName(), id);

        return entityToModel(entity);
    }

    /** PAT-249: what stands between the measure and its approval, with the four-eyes verdict for {@code currentUser}. */
    @Transactional(readOnly = true)
    public com.cqlplatform.model.measure.ApprovalReadiness getApprovalReadiness(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        return readinessService.check(entity, currentUser);
    }

    @Transactional
    public MeasureDefinition rejectMeasure(Long id, String reason, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkReviewer(entity, currentUser);
        validateTransition(entity.getStatus(), DRAFT);

        String oldStatus = entity.getStatus();
        entity.setStatus(DRAFT);
        entity.setReviewedBy(currentUser);
        entity.setReviewedAt(java.time.LocalDateTime.now());
        entity.setReviewComment(reason);
        entity.setApprovedBy(null);
        entity = repository.save(entity);
        recordAudit(id, "REJECT", currentUser, "Rejected: " + (reason != null ? reason : "no reason"), oldStatus, DRAFT);
        log.info("Measure {} rejected by {}: {}", id, currentUser, reason);

        // Notify the measure owner
        notificationService.notifyMeasureRejected(currentUser, entity.getOwnerUsername(), entity.getName(), id, reason);

        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition retireMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        validateTransition(entity.getStatus(), RETIRED);

        String oldStatus = entity.getStatus();
        entity.setStatus(RETIRED);
        entity = repository.save(entity);
        recordAudit(id, "RETIRE", currentUser, "Retired", oldStatus, RETIRED);
        log.info("Measure {} retired by {}", id, currentUser);
        return entityToModel(entity);
    }

    /**
     * BUG-147 — approving a new version replaces the approved one: every other {@code active}
     * version of the same measure (tenant + name) is retired, and the evaluation schedules that
     * pointed at it move to the newly approved version, so scheduled runs keep going on approved
     * logic instead of hitting the lifecycle guard.
     */
    private void supersedeOtherActiveVersions(MeasureDefinitionEntity approved, String currentUser) {
        // PAT-253: the lineage is the measure set (name-based only for rows without one)
        for (MeasureDefinitionEntity other : lineageOf(approved.getTenantId(), approved.getMeasureSetId(), approved.getName())) {
            if (other.getId().equals(approved.getId()) || !ACTIVE.equals(other.getStatus())) continue;
            other.setStatus(RETIRED);
            repository.save(other);
            recordAudit(other.getId(), "SUPERSEDE", currentUser,
                    "Retired: superseded by v" + approved.getVersion() + " (measure " + approved.getId() + ")", ACTIVE, RETIRED);
            for (com.cqlplatform.entity.MeasureScheduleEntity schedule : scheduleRepository.findByMeasureDefinitionId(other.getId())) {
                schedule.setMeasureDefinitionId(approved.getId());
                scheduleRepository.save(schedule);
            }
            log.info("Measure {} v{} superseded by {} v{}", other.getId(), other.getVersion(), approved.getId(), approved.getVersion());
        }
    }

    private void validateTransition(String currentStatus, String targetStatus) {
        List<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, List.of());
        if (!allowed.contains(targetStatus)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + currentStatus + " → " + targetStatus +
                    ". Allowed: " + allowed);
        }
    }

    private void checkOwner(MeasureDefinitionEntity entity, String currentUser) {
        if (entity.getOwnerUsername() != null && !entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can perform this action");
        }
    }

    /** PAT-262: usernames of the assigned reviewers (empty when the measure uses the sharing-based rule). */
    private List<String> assignedReviewers(MeasureDefinitionEntity entity) {
        return reviewerRepository.findByMeasureIdAndTenantIdOrderByAssignedAtAsc(entity.getId(), entity.getTenantId())
                .stream().map(com.cqlplatform.entity.MeasureReviewerEntity::getUsername).toList();
    }

    /**
     * PAT-262: shares the measure (across its set) with {@code username} without the owner check —
     * the caller has already decided who may do this (reviewer assignment by owner or admin).
     */
    @Transactional
    public void ensureSharedWith(Long id, String username, String actor) {
        MeasureDefinitionEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        if (username == null || username.isBlank() || entity.getSharedWithList().contains(username)
                || username.equals(entity.getOwnerUsername())) {
            return;
        }
        applyAcrossSet(entity, member -> {
            List<String> shared = new ArrayList<>(member.getSharedWithList());
            if (!shared.contains(username)) shared.add(username);
            member.setSharedWithList(shared);
            if ("private".equals(member.getAccessLevel())) member.setAccessLevel("shared");
        });
        recordAudit(id, "SHARE", actor, "Shared with " + username + " (assigned as reviewer)", null, null);
    }

    private void checkReviewer(MeasureDefinitionEntity entity, String currentUser) {
        // PAT-262: once reviewers are assigned, only they (or an admin) review the measure
        List<String> assigned = assignedReviewers(entity);
        if (!assigned.isEmpty()) {
            if (assigned.contains(currentUser) || ownershipVerifier.isAdmin()) return;
            throw new com.cqlplatform.exception.ApprovalNotAllowedException(
                    "Only the assigned reviewers of measure '" + entity.getName() + "' (" + String.join(", ", assigned)
                            + ") may approve or reject it");
        }
        // Reviewers: sharedWith users or the owner
        boolean isOwner = entity.getOwnerUsername() == null || entity.getOwnerUsername().equals(currentUser);
        boolean isShared = entity.getSharedWithList().contains(currentUser);
        if (!isOwner && !isShared) {
            throw new IllegalArgumentException("Only the owner or shared users can review this measure");
        }
    }

    // ===== Audit =====

    @Transactional(readOnly = true)
    public List<MeasureAuditEntity> getAuditTrail(Long measureId) {
        return auditRepository.findByMeasureIdOrderByCreatedAtDesc(measureId);
    }

    private void recordAudit(Long measureId, String action, String performedBy, String details, String oldValue, String newValue) {
        MeasureAuditEntity audit = MeasureAuditEntity.builder()
                .measureId(measureId)
                .action(action)
                .performedBy(performedBy)
                .details(details)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditRepository.save(audit);
    }

    private MeasureDefinitionEntity modelToEntity(MeasureDefinition model) {
        return MeasureDefinitionEntity.builder()
                .name(model.getName())
                .version(model.getVersion() != null ? model.getVersion() : "1.0.0")
                .title(model.getTitle())
                .description(model.getDescription())
                .status(model.getStatus() != null ? model.getStatus() : DRAFT)
                .scoringType(model.getScoringType() != null ? model.getScoringType() : ScoringTypeConstants.PROPORTION)
                .cqlLibraryId(model.getCqlLibraryId())
                .cqlContent(model.getCqlContent())
                .elmJson(model.getElmJson())
                .fhirMeasureJson(model.getFhirMeasureJson())
                .groupDefinitionList(model.getGroupDefinitions())
                .compositeScoring(model.getCompositeScoring())
                .componentMeasureIdList(model.getComponentMeasureIds())
                .componentWeightMap(weightMap(model.getComponentWeights()))          // PAT-259
                .valueSetVersionMap(pinMap(model.getValueSetVersions()))              // PAT-260
                .createdBy(model.getCreatedBy())
                .ownerUsername(model.getOwnerUsername())
                .sharedWithList(model.getSharedWith())
                .accessLevel(model.getAccessLevel() != null ? model.getAccessLevel() : "private")
                .setting(model.getSetting())
                // Enhanced metadata
                .rationale(model.getRationale())
                .clinicalGuidance(model.getClinicalGuidance())
                .steward(model.getSteward())
                .developerList(model.getDevelopers())
                .referenceList(model.getReferences())
                .disclaimer(model.getDisclaimer())
                .copyright(model.getCopyright())
                .measureSet(model.getMeasureSet())
                .nqfNumber(model.getNqfNumber())
                .cmsMeasureId(model.getCmsMeasureId())
                .ecqmTitle(model.getEcqmTitle())
                .endorser(model.getEndorser())
                .endorsementId(model.getEndorsementId())
                .supplementalDataGuidance(model.getSupplementalDataGuidance())
                .riskAdjustmentDescription(model.getRiskAdjustmentDescription())
                .riskAdjustmentList(model.getRiskAdjustments())
                .supplementalDataList(model.getSupplementalData())
                .improvementNotation(model.getImprovementNotation())
                .rateAggregation(model.getRateAggregation())
                // PAT-236 standard metadata
                .measureTypeList(model.getMeasureTypes() != null ? model.getMeasureTypes() : new java.util.ArrayList<>())
                .definitionTermList(model.getDefinitionTerms() != null ? model.getDefinitionTerms() : new java.util.ArrayList<>())
                .clinicalRecommendationStatement(model.getClinicalRecommendationStatement())
                .effectiveStart(model.getEffectiveStart())
                .effectiveEnd(model.getEffectiveEnd())
                .approvalDate(model.getApprovalDate())
                .lastReviewDate(model.getLastReviewDate())
                .experimental(model.getExperimental())
                .measurementPeriodStart(model.getMeasurementPeriodStart())
                .measurementPeriodEnd(model.getMeasurementPeriodEnd())
                .measurementPeriodStart(model.getMeasurementPeriodStart())
                .measurementPeriodEnd(model.getMeasurementPeriodEnd())
                // Indicator code mapping
                .mohIndicatorCode(model.getMohIndicatorCode())
                .nhiaP4pCode(model.getNhiaP4pCode())
                .drgIndicatorCode(model.getDrgIndicatorCode())
                .indicatorCategory(model.getIndicatorCategory())
                .department(model.getDepartment())
                .tenantId(effectiveTenantId())
                .build();
    }

    /**
     * Pre-compile CQL to ELM JSON. Returns null if CQL is blank.
     *
     * <p>Translation errors are propagated as {@link CqlTranslationException} so the
     * controller layer maps to HTTP 400 with the error list, rather than silently
     * persisting the measure with a null {@code elmJson} — the previous behaviour
     * meant a save with broken CQL appeared to succeed and only failed later during
     * evaluation with a cryptic "library not found" / runtime translation error
     * (BUG-110/111 family). The transactional boundary on {@code create}/{@code update}
     * rolls the save back so users see the error before any state change.
     */
    private String preCompileElm(String cql) {
        if (cql == null || cql.isBlank()) return null;
        CqlTranslationResponse resp;
        try {
            var req = new CqlTranslationRequest();
            req.setCql(cql);
            resp = cqlTranslationService.translate(req);
        } catch (Exception e) {
            // Translator threw — wrap so the caller gets a structured error envelope
            // rather than the raw stack-trace 500.
            throw new CqlTranslationException(
                    "CQL translation failed: " + e.getMessage());
        }
        if (resp.isSuccess()) {
            return resp.getElmJson();
        }
        throw new CqlTranslationException(
                "CQL translation failed; measure not saved", resp.getErrors());
    }
}
