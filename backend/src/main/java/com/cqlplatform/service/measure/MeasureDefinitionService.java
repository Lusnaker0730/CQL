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

    /** Effective tenant: the caller's, or the default tenant for legacy callers with none. */
    private Long effectiveTenantId() {
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
                definition.getComponentMeasureIds(), definition.getCqlLibraryId())) {
            throw new com.cqlplatform.exception.MeasureLogicLockedException(id, entity.getStatus());
        }

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

    @Transactional(readOnly = true)
    public Map<String, Object> compare(Long oldId, Long newId) {
        MeasureDefinition oldMeasure = getById(oldId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + oldId));
        MeasureDefinition newMeasure = getById(newId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + newId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldCql", oldMeasure.getCqlContent() != null ? oldMeasure.getCqlContent() : "");
        result.put("newCql", newMeasure.getCqlContent() != null ? newMeasure.getCqlContent() : "");
        result.put("oldVersion", oldMeasure.getVersion());
        result.put("newVersion", newMeasure.getVersion());

        // Enhanced: metadata changes
        List<String> metadataChanges = new ArrayList<>();
        diffField(metadataChanges, "Title", oldMeasure.getTitle(), newMeasure.getTitle());
        diffField(metadataChanges, "Description", oldMeasure.getDescription(), newMeasure.getDescription());
        diffField(metadataChanges, "Status", oldMeasure.getStatus(), newMeasure.getStatus());
        diffField(metadataChanges, "Scoring Type", oldMeasure.getScoringType(), newMeasure.getScoringType());
        diffField(metadataChanges, "Steward", oldMeasure.getSteward(), newMeasure.getSteward());
        diffField(metadataChanges, "Rationale", oldMeasure.getRationale(), newMeasure.getRationale());
        result.put("metadataChanges", metadataChanges);

        // Enhanced: population changes
        List<String> populationChanges = computePopulationChanges(oldMeasure, newMeasure);
        result.put("populationChanges", populationChanges);

        return result;
    }

    private void diffField(List<String> changes, String fieldName, String oldVal, String newVal) {
        String o = oldVal != null ? oldVal : "";
        String n = newVal != null ? newVal : "";
        if (!o.equals(n)) {
            if (o.isEmpty()) {
                changes.add(fieldName + " added: \"" + n + "\"");
            } else if (n.isEmpty()) {
                changes.add(fieldName + " removed (was: \"" + o + "\")");
            } else {
                changes.add(fieldName + " changed: \"" + o + "\" → \"" + n + "\"");
            }
        }
    }

    private List<String> computePopulationChanges(MeasureDefinition oldM, MeasureDefinition newM) {
        List<String> changes = new ArrayList<>();
        var oldGroups = oldM.getGroupDefinitions() != null ? oldM.getGroupDefinitions() : List.<com.cqlplatform.model.measure.GroupDefinition>of();
        var newGroups = newM.getGroupDefinitions() != null ? newM.getGroupDefinitions() : List.<com.cqlplatform.model.measure.GroupDefinition>of();

        int maxGroups = Math.max(oldGroups.size(), newGroups.size());
        for (int i = 0; i < maxGroups; i++) {
            if (i >= oldGroups.size()) {
                changes.add("Group " + newGroups.get(i).getGroupId() + " added");
                continue;
            }
            if (i >= newGroups.size()) {
                changes.add("Group " + oldGroups.get(i).getGroupId() + " removed");
                continue;
            }
            var og = oldGroups.get(i);
            var ng = newGroups.get(i);
            int oldPopCount = og.getPopulations() != null ? og.getPopulations().size() : 0;
            int newPopCount = ng.getPopulations() != null ? ng.getPopulations().size() : 0;
            if (oldPopCount != newPopCount) {
                changes.add(og.getGroupId() + ": population count changed " + oldPopCount + " → " + newPopCount);
            }
        }
        return changes;
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

        // Notify shared users (reviewers)
        notificationService.notifyMeasureSubmitted(currentUser, entity.getSharedWithList(), entity.getName(), id);

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

    private void checkReviewer(MeasureDefinitionEntity entity, String currentUser) {
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
