package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.service.cql.SemanticVersionComparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasureDefinitionService {

    private final MeasureDefinitionRepository repository;
    private final MeasureAuditRepository auditRepository;

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

        // Enhanced metadata
        entity.setRationale(definition.getRationale());
        entity.setClinicalGuidance(definition.getClinicalGuidance());
        entity.setSteward(definition.getSteward());
        entity.setDeveloperList(definition.getDevelopers());
        entity.setReferenceList(definition.getReferences());
        entity.setDisclaimer(definition.getDisclaimer());
        entity.setCopyright(definition.getCopyright());
        entity.setMeasureSet(definition.getMeasureSet());
        entity.setSupplementalDataGuidance(definition.getSupplementalDataGuidance());
        entity.setRiskAdjustmentDescription(definition.getRiskAdjustmentDescription());
        entity.setRiskAdjustmentList(definition.getRiskAdjustments());
        entity.setSupplementalDataList(definition.getSupplementalData());

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
        if (searchTerm == null || searchTerm.isBlank()) {
            return getAll();
        }
        return repository.findByNameContainingIgnoreCaseOrTitleContainingIgnoreCase(searchTerm, searchTerm)
                .stream()
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
        existing.setStatus("active");
        repository.save(existing);

        // Create new draft copy
        MeasureDefinitionEntity newEntity = modelToEntity(entityToModel(existing));
        newEntity.setId(null);
        newEntity.setVersion(newVersion);
        newEntity.setStatus("draft");
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
    public Map<String, String> compare(Long oldId, Long newId) {
        MeasureDefinition oldMeasure = getById(oldId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + oldId));
        MeasureDefinition newMeasure = getById(newId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + newId));

        Map<String, String> result = new LinkedHashMap<>();
        result.put("oldCql", oldMeasure.getCqlContent() != null ? oldMeasure.getCqlContent() : "");
        result.put("newCql", newMeasure.getCqlContent() != null ? newMeasure.getCqlContent() : "");
        result.put("oldVersion", oldMeasure.getVersion());
        result.put("newVersion", newMeasure.getVersion());
        return result;
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
                // Enhanced metadata
                .rationale(entity.getRationale())
                .clinicalGuidance(entity.getClinicalGuidance())
                .steward(entity.getSteward())
                .developers(entity.getDeveloperList())
                .references(entity.getReferenceList())
                .disclaimer(entity.getDisclaimer())
                .copyright(entity.getCopyright())
                .measureSet(entity.getMeasureSet())
                .supplementalDataGuidance(entity.getSupplementalDataGuidance())
                .riskAdjustmentDescription(entity.getRiskAdjustmentDescription())
                .riskAdjustments(entity.getRiskAdjustmentList())
                .supplementalData(entity.getSupplementalDataList())
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

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getMeasuresByOwner(String ownerUsername) {
        return repository.findByOwnerUsername(ownerUsername).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeasureDefinition> getSharedMeasures(String username) {
        return repository.findAll().stream()
                .filter(e -> e.getSharedWithList().contains(username) || "public".equals(e.getAccessLevel()))
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    // ===== Workflow =====

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
            "draft", List.of("in-review"),
            "in-review", List.of("active", "draft"),
            "active", List.of("retired")
    );

    @Transactional
    public MeasureDefinition submitForReview(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        validateTransition(entity.getStatus(), "in-review");

        String oldStatus = entity.getStatus();
        entity.setStatus("in-review");
        entity = repository.save(entity);
        recordAudit(id, "SUBMIT_FOR_REVIEW", currentUser, "Submitted for review", oldStatus, "in-review");
        log.info("Measure {} submitted for review by {}", id, currentUser);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition approveMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkReviewer(entity, currentUser);
        validateTransition(entity.getStatus(), "active");

        String oldStatus = entity.getStatus();
        entity.setStatus("active");
        entity = repository.save(entity);
        recordAudit(id, "APPROVE", currentUser, "Approved and set to active", oldStatus, "active");
        log.info("Measure {} approved by {}", id, currentUser);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition rejectMeasure(Long id, String reason, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkReviewer(entity, currentUser);
        validateTransition(entity.getStatus(), "draft");

        String oldStatus = entity.getStatus();
        entity.setStatus("draft");
        entity = repository.save(entity);
        recordAudit(id, "REJECT", currentUser, "Rejected: " + (reason != null ? reason : "no reason"), oldStatus, "draft");
        log.info("Measure {} rejected by {}: {}", id, currentUser, reason);
        return entityToModel(entity);
    }

    @Transactional
    public MeasureDefinition retireMeasure(Long id, String currentUser) {
        MeasureDefinitionEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + id));
        checkOwner(entity, currentUser);
        validateTransition(entity.getStatus(), "retired");

        String oldStatus = entity.getStatus();
        entity.setStatus("retired");
        entity = repository.save(entity);
        recordAudit(id, "RETIRE", currentUser, "Retired", oldStatus, "retired");
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
                .status(model.getStatus() != null ? model.getStatus() : "draft")
                .scoringType(model.getScoringType() != null ? model.getScoringType() : "proportion")
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
                // Enhanced metadata
                .rationale(model.getRationale())
                .clinicalGuidance(model.getClinicalGuidance())
                .steward(model.getSteward())
                .developerList(model.getDevelopers())
                .referenceList(model.getReferences())
                .disclaimer(model.getDisclaimer())
                .copyright(model.getCopyright())
                .measureSet(model.getMeasureSet())
                .supplementalDataGuidance(model.getSupplementalDataGuidance())
                .riskAdjustmentDescription(model.getRiskAdjustmentDescription())
                .riskAdjustmentList(model.getRiskAdjustments())
                .supplementalDataList(model.getSupplementalData())
                .build();
    }
}
