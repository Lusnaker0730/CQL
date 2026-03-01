package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.NotificationService;
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

    @Value("${measure.locking.timeout-minutes:30}")
    private int lockTimeoutMinutes;

    @Transactional
    public MeasureDefinition create(MeasureDefinition definition) {
        if (repository.existsByNameAndVersion(definition.getName(), definition.getVersion())) {
            throw new IllegalArgumentException(
                    "Measure already exists: " + definition.getName() + " v" + definition.getVersion());
        }

        MeasureDefinitionEntity entity = modelToEntity(definition);
        entity = repository.save(entity);
        log.info("Created measure definition: {} v{}", entity.getName(), entity.getVersion());
        recordAudit(entity.getId(), "CREATE", entity.getCreatedBy(), "Created " + entity.getName() + " v" + entity.getVersion(), null, null);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition update(Long id, MeasureDefinition definition) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        // Check lock — only the lock holder can save while locked
        if (entity.getLockedBy() != null && !isLockExpired(entity)) {
            String currentUser = definition.getOwnerUsername(); // caller identity passed via ownerUsername
            if (currentUser == null || !entity.getLockedBy().equals(currentUser)) {
                throw new IllegalArgumentException("Measure is locked by " + entity.getLockedBy());
            }
        }

        entity.setName(definition.getName());
        entity.setVersion(definition.getVersion());
        entity.setTitle(definition.getTitle());
        entity.setDescription(definition.getDescription());
        entity.setStatus(definition.getStatus());
        entity.setScoringType(definition.getScoringType());
        entity.setCqlLibraryId(definition.getCqlLibraryId());
        entity.setCqlContent(definition.getCqlContent());
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

        // Indicator code mapping
        entity.setMohIndicatorCode(definition.getMohIndicatorCode());
        entity.setNhiaP4pCode(definition.getNhiaP4pCode());
        entity.setDrgIndicatorCode(definition.getDrgIndicatorCode());
        entity.setIndicatorCategory(definition.getIndicatorCategory());
        entity.setDepartment(definition.getDepartment());

        // Sharing fields from update
        if (definition.getOwnerUsername() != null) {
            entity.setOwnerUsername(definition.getOwnerUsername());
        }
        if (definition.getSharedWith() != null) {
            entity.setSharedWithList(definition.getSharedWith());
        }
        if (definition.getAccessLevel() != null) {
            entity.setAccessLevel(definition.getAccessLevel());
        }

        entity = repository.save(entity);
        log.info("Updated measure definition: {} v{}", entity.getName(), entity.getVersion());
        recordAudit(entity.getId(), "UPDATE", null, "Updated " + entity.getName() + " v" + entity.getVersion(), null, null);
        return entityToModel(entity);
    }

    @Transactional(readOnly = true)
    public Optional<MeasureDefinition> getById(Long id) {
        return repository.findById(id).map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public Optional<MeasureDefinition> getByNameAndVersion(String name, String version) {
        return repository.findByNameAndVersion(name, version).map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getAll() {
        return repository.findAll().stream()
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

        List<MeasureDefinitionEntity> entities;
        if (hasSearch && hasDept) {
            entities = repository.findByDepartmentAndSearchTerm(department, InputValidator.escapeLikeWildcards(searchTerm));
        } else if (hasDept) {
            entities = repository.findByDepartment(department);
        } else if (hasSearch) {
            entities = repository.findByNameContainingIgnoreCaseOrTitleContainingIgnoreCase(searchTerm, searchTerm);
        } else {
            entities = repository.findAll();
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
        MeasureDefinitionEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        String newVersion = bumpVersion(existing.getVersion(), versionType);

        if (repository.existsByNameAndVersion(existing.getName(), newVersion)) {
            throw new IllegalArgumentException("Version already exists: " + existing.getName() + " v" + newVersion);
        }

        // Set current to active
        existing.setStatus(ACTIVE);
        repository.save(existing);

        // Create new draft copy
        MeasureDefinitionEntity newEntity = modelToEntity(entityToModel(existing));
        newEntity.setId(null);
        newEntity.setVersion(newVersion);
        newEntity.setStatus(DRAFT);
        newEntity = repository.save(newEntity);

        log.info("Created version {} for measure {}", newVersion, existing.getName());
        return entityToModel(newEntity);
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getHistory(String name) {
        return repository.findByName(name).stream()
                .sorted(Comparator.comparing(MeasureDefinitionEntity::getVersion, new SemanticVersionComparator()).reversed())
                .map(this::entityToModel)
                .collect(Collectors.toList());
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
        return MeasureDefinition.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .scoringType(entity.getScoringType())
                .cqlLibraryId(entity.getCqlLibraryId())
                .cqlContent(entity.getCqlContent())
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
                .lockedBy(entity.getLockedBy())
                .lockedAt(entity.getLockedAt())
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
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);

        List<String> shared = new ArrayList<>(entity.getSharedWithList());
        if (!shared.contains(targetUsername)) {
            shared.add(targetUsername);
        }
        entity.setSharedWithList(shared);
        if ("private".equals(entity.getAccessLevel())) {
            entity.setAccessLevel("shared");
        }
        entity = repository.save(entity);
        recordAudit(id, "SHARE", currentUser, "Shared with " + targetUsername, null, null);
        log.info("Shared measure {} with user {}", id, targetUsername);

        // Notify the target user
        notificationService.notifyMeasureShared(currentUser, targetUsername, entity.getName(), id);

        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition unshareMeasure(Long id, String targetUsername, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);

        List<String> shared = new ArrayList<>(entity.getSharedWithList());
        shared.remove(targetUsername);
        entity.setSharedWithList(shared);
        if (shared.isEmpty() && "shared".equals(entity.getAccessLevel())) {
            entity.setAccessLevel("private");
        }
        entity = repository.save(entity);
        recordAudit(id, "UNSHARE", currentUser, "Removed sharing for " + targetUsername, null, null);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition transferOwnership(Long id, String newOwner, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);

        String oldOwner = entity.getOwnerUsername();
        entity.setOwnerUsername(newOwner);
        entity = repository.save(entity);
        recordAudit(id, "TRANSFER", currentUser, "Transferred from " + oldOwner + " to " + newOwner, oldOwner, newOwner);
        log.info("Transferred measure {} from {} to {}", id, currentUser, newOwner);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition setAccessLevel(Long id, String accessLevel, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);

        String oldLevel = entity.getAccessLevel();
        entity.setAccessLevel(accessLevel);
        entity = repository.save(entity);
        recordAudit(id, "ACCESS_CHANGE", currentUser, "Access level changed", oldLevel, accessLevel);
        return entityToModel(entity);
    }

    // ===== Locking =====

    @Transactional
    public MeasureDefinition lockMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        if (entity.getLockedBy() != null && !isLockExpired(entity) && !entity.getLockedBy().equals(currentUser)) {
            throw new IllegalArgumentException("Measure is already locked by " + entity.getLockedBy());
        }

        entity.setLockedBy(currentUser);
        entity.setLockedAt(java.time.LocalDateTime.now());
        entity = repository.save(entity);
        recordAudit(id, "LOCK", currentUser, "Locked by " + currentUser, null, null);
        log.info("Measure {} locked by {}", id, currentUser);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition unlockMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));

        if (entity.getLockedBy() == null) {
            return entityToModel(entity);
        }

        // Only the lock holder or the owner can unlock
        boolean isLockHolder = entity.getLockedBy().equals(currentUser);
        boolean isOwner = entity.getOwnerUsername() == null || entity.getOwnerUsername().equals(currentUser);
        if (!isLockHolder && !isOwner) {
            throw new IllegalArgumentException("Only the lock holder or owner can unlock this measure");
        }

        String previousHolder = entity.getLockedBy();
        entity.setLockedBy(null);
        entity.setLockedAt(null);
        entity = repository.save(entity);
        recordAudit(id, "UNLOCK", currentUser, "Unlocked (was locked by " + previousHolder + ")", null, null);
        log.info("Measure {} unlocked by {}", id, currentUser);
        return entityToModel(entity);
    }

    private boolean isLockExpired(MeasureDefinitionEntity entity) {
        if (entity.getLockedAt() == null) return true;
        return entity.getLockedAt().plusMinutes(lockTimeoutMinutes).isBefore(java.time.LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getMeasuresByOwner(String ownerUsername) {
        return repository.findByOwnerUsername(ownerUsername).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getSharedMeasures(String username) {
        return repository.findSharedWithUser("%\"" + username + "\"%").stream()
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
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        validateTransition(entity.getStatus(), IN_REVIEW);

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
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkReviewer(entity, currentUser);
        validateTransition(entity.getStatus(), ACTIVE);

        String oldStatus = entity.getStatus();
        entity.setStatus(ACTIVE);
        entity.setApprovedBy(currentUser);
        entity.setReviewedBy(currentUser);
        entity.setReviewedAt(java.time.LocalDateTime.now());
        entity.setReviewComment(null);
        entity = repository.save(entity);
        recordAudit(id, "APPROVE", currentUser, "Approved and set to active", oldStatus, ACTIVE);
        log.info("Measure {} approved by {}", id, currentUser);

        // Notify the measure owner
        notificationService.notifyMeasureApproved(currentUser, entity.getOwnerUsername(), entity.getName(), id);

        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition rejectMeasure(Long id, String reason, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
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
        MeasureDefinitionEntity entity = repository.findById(id)
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
                // Indicator code mapping
                .mohIndicatorCode(model.getMohIndicatorCode())
                .nhiaP4pCode(model.getNhiaP4pCode())
                .drgIndicatorCode(model.getDrgIndicatorCode())
                .indicatorCategory(model.getIndicatorCategory())
                .department(model.getDepartment())
                .build();
    }
}
