package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.model.ecqm.EcqmArtifactRequest;
import com.cqlplatform.model.ecqm.EcqmArtifactResponse;
import com.cqlplatform.model.ecqm.EcqmArtifactSummary;
import com.cqlplatform.repository.EcqmArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcqmArtifactService {

    private final EcqmArtifactRepository repository;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

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

    @Transactional(readOnly = true)
    public List<EcqmArtifactSummary> listByOwner(String ownerUsername) {
        return repository.findByTenantIdAndOwnerUsername(effectiveTenantId(), ownerUsername).stream()
                .map(this::entityToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<EcqmArtifactResponse> getById(Long id) {
        return repository.findByIdAndTenantId(id, effectiveTenantId()).map(this::entityToResponse);
    }

    @Transactional
    public EcqmArtifactResponse create(EcqmArtifactRequest request, String ownerUsername) {
        EcqmArtifactEntity entity = requestToEntity(request);
        entity.setOwnerUsername(ownerUsername);
        entity.setTenantId(effectiveTenantId()); // server-assigned tenant
        entity = repository.save(entity);
        log.info("Created eCQM artifact: {} v{} by {}", entity.getName(), entity.getVersion(), ownerUsername);
        return entityToResponse(entity);
    }

    @Transactional
    public EcqmArtifactResponse update(Long id, EcqmArtifactRequest request, String currentUser) {
        EcqmArtifactEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("eCQM artifact not found: " + id));
        checkOwner(entity, currentUser);

        // All fields use null guards to support partial updates — the frontend sends
        // only changed fields to avoid race conditions where stale data overwrites
        // recently saved changes (e.g. population groups lost after a summary field edit).
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getVersion() != null) entity.setVersion(request.getVersion());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getScoringType() != null) entity.setScoringType(request.getScoringType());
        if (request.getPopulationBasis() != null) entity.setPopulationBasis(request.getPopulationBasis());
        if (request.getImprovementNotation() != null) entity.setImprovementNotation(request.getImprovementNotation());
        if (request.getMeasureSet() != null) entity.setMeasureSet(request.getMeasureSet());
        if (request.getCmsMeasureId() != null) entity.setCmsMeasureId(request.getCmsMeasureId());
        if (request.getNqfNumber() != null) entity.setNqfNumber(request.getNqfNumber());
        if (request.getUrl() != null) entity.setUrl(request.getUrl());
        if (request.getPublisher() != null) entity.setPublisher(request.getPublisher());
        if (request.getPurpose() != null) entity.setPurpose(request.getPurpose());
        if (request.getCopyright() != null) entity.setCopyright(request.getCopyright());
        if (request.getRationale() != null) entity.setRationale(request.getRationale());
        if (request.getClinicalGuidance() != null) entity.setClinicalGuidance(request.getClinicalGuidance());
        if (request.getSteward() != null) entity.setSteward(request.getSteward());
        if (request.getDisclaimer() != null) entity.setDisclaimer(request.getDisclaimer());
        if (request.getSupplementalDataGuidance() != null) entity.setSupplementalDataGuidance(request.getSupplementalDataGuidance());

        if (request.getPopulationGroups() != null) entity.setPopulationGroupsList(request.getPopulationGroups());
        if (request.getSupplementalData() != null) entity.setSupplementalDataList(request.getSupplementalData());
        if (request.getStratifiers() != null) entity.setStratifiersList(request.getStratifiers());
        if (request.getBaseElements() != null) entity.setBaseElementsList(request.getBaseElements());
        if (request.getParameters() != null) entity.setParametersList(request.getParameters());

        // Explicitly serialize transient list fields → JSON columns before save.
        // The @Transient lists are invisible to Hibernate's dirty checking, so if only
        // list fields changed (and no persistent column changed), @PreUpdate won't fire
        // and the JSON columns won't be updated. Calling serializeAll() ensures the
        // JSON columns always reflect the latest list data.
        entity.serializeAll();

        entity = repository.save(entity);
        log.info("Updated eCQM artifact: {} (id={})", entity.getName(), entity.getId());
        return entityToResponse(entity);
    }

    @Transactional
    public void delete(Long id, String currentUser) {
        EcqmArtifactEntity entity = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("eCQM artifact not found: " + id));
        checkOwner(entity, currentUser);
        repository.deleteById(id);
        log.info("Deleted eCQM artifact: {} (id={})", entity.getName(), id);
    }

    @Transactional
    public EcqmArtifactResponse duplicate(Long id, String currentUser) {
        EcqmArtifactEntity original = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("eCQM artifact not found: " + id));
        checkOwner(original, currentUser);

        EcqmArtifactEntity copy = EcqmArtifactEntity.builder()
                .name(original.getName() + " (Copy)")
                .version(original.getVersion())
                .description(original.getDescription())
                .status("draft")
                .fhirVersion(original.getFhirVersion())
                .scoringType(original.getScoringType())
                .populationBasis(original.getPopulationBasis())
                .improvementNotation(original.getImprovementNotation())
                .measureSet(original.getMeasureSet())
                .cmsMeasureId(null)
                .nqfNumber(original.getNqfNumber())
                .url(original.getUrl())
                .publisher(original.getPublisher())
                .purpose(original.getPurpose())
                .copyright(original.getCopyright())
                .rationale(original.getRationale())
                .clinicalGuidance(original.getClinicalGuidance())
                .steward(original.getSteward())
                .disclaimer(original.getDisclaimer())
                .supplementalDataGuidance(original.getSupplementalDataGuidance())
                .populationGroupsList(new ArrayList<>(original.getPopulationGroupsList()))
                .supplementalDataList(new ArrayList<>(original.getSupplementalDataList()))
                .stratifiersList(new ArrayList<>(original.getStratifiersList()))
                .baseElementsList(new ArrayList<>(original.getBaseElementsList()))
                .parametersList(new ArrayList<>(original.getParametersList()))
                .ownerUsername(currentUser)
                .tenantId(original.getTenantId()) // copy stays in the source tenant
                .build();

        copy = repository.save(copy);
        log.info("Duplicated eCQM artifact {} → {} (id={})", original.getId(), copy.getName(), copy.getId());
        return entityToResponse(copy);
    }

    private void checkOwner(EcqmArtifactEntity entity, String currentUser) {
        if (!entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can perform this action");
        }
    }

    private EcqmArtifactResponse entityToResponse(EcqmArtifactEntity entity) {
        return EcqmArtifactResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .fhirVersion(entity.getFhirVersion())
                .scoringType(entity.getScoringType())
                .populationBasis(entity.getPopulationBasis())
                .improvementNotation(entity.getImprovementNotation())
                .measureSet(entity.getMeasureSet())
                .cmsMeasureId(entity.getCmsMeasureId())
                .nqfNumber(entity.getNqfNumber())
                .url(entity.getUrl())
                .publisher(entity.getPublisher())
                .purpose(entity.getPurpose())
                .copyright(entity.getCopyright())
                .rationale(entity.getRationale())
                .clinicalGuidance(entity.getClinicalGuidance())
                .steward(entity.getSteward())
                .disclaimer(entity.getDisclaimer())
                .supplementalDataGuidance(entity.getSupplementalDataGuidance())
                .populationGroups(entity.getPopulationGroupsList())
                .supplementalData(entity.getSupplementalDataList())
                .stratifiers(entity.getStratifiersList())
                .baseElements(entity.getBaseElementsList())
                .parameters(entity.getParametersList())
                .publishedMeasureId(entity.getPublishedMeasureId())
                .ownerUsername(entity.getOwnerUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private EcqmArtifactSummary entityToSummary(EcqmArtifactEntity entity) {
        return EcqmArtifactSummary.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .scoringType(entity.getScoringType())
                .populationBasis(entity.getPopulationBasis())
                .publishedMeasureId(entity.getPublishedMeasureId())
                .ownerUsername(entity.getOwnerUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private EcqmArtifactEntity requestToEntity(EcqmArtifactRequest request) {
        return EcqmArtifactEntity.builder()
                .name(request.getName())
                .version(request.getVersion() != null ? request.getVersion() : "1.0.0")
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "draft")
                .fhirVersion("4.0.1")
                .scoringType(request.getScoringType() != null ? request.getScoringType() : "proportion")
                .populationBasis(request.getPopulationBasis() != null ? request.getPopulationBasis() : "boolean")
                .improvementNotation(request.getImprovementNotation() != null ? request.getImprovementNotation() : "increase")
                .measureSet(request.getMeasureSet())
                .cmsMeasureId(request.getCmsMeasureId())
                .nqfNumber(request.getNqfNumber())
                .url(request.getUrl())
                .publisher(request.getPublisher())
                .purpose(request.getPurpose())
                .copyright(request.getCopyright())
                .rationale(request.getRationale())
                .clinicalGuidance(request.getClinicalGuidance())
                .steward(request.getSteward())
                .disclaimer(request.getDisclaimer())
                .supplementalDataGuidance(request.getSupplementalDataGuidance())
                .populationGroupsList(request.getPopulationGroups() != null ? request.getPopulationGroups() : new ArrayList<>())
                .supplementalDataList(request.getSupplementalData() != null ? request.getSupplementalData() : new ArrayList<>())
                .stratifiersList(request.getStratifiers() != null ? request.getStratifiers() : new ArrayList<>())
                .baseElementsList(request.getBaseElements() != null ? request.getBaseElements() : new ArrayList<>())
                .parametersList(request.getParameters() != null ? request.getParameters() : new ArrayList<>())
                .build();
    }
}
