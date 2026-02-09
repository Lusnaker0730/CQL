package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.MeasureDefinition;
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

    @Transactional
    public MeasureDefinition create(MeasureDefinition definition) {
        if (repository.existsByNameAndVersion(definition.getName(), definition.getVersion())) {
            throw new IllegalArgumentException(
                    "Measure already exists: " + definition.getName() + " v" + definition.getVersion());
        }

        MeasureDefinitionEntity entity = modelToEntity(definition);
        entity = repository.save(entity);
        log.info("Created measure definition: {} v{}", entity.getName(), entity.getVersion());
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

        entity = repository.save(entity);
        log.info("Updated measure definition: {} v{}", entity.getName(), entity.getVersion());
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
