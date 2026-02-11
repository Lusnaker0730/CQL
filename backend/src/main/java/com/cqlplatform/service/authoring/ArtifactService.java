package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.authoring.ArtifactRequest;
import com.cqlplatform.model.authoring.ArtifactResponse;
import com.cqlplatform.model.authoring.ArtifactSummary;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.repository.CdsExternalCqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final CdsArtifactRepository artifactRepository;
    private final CdsExternalCqlLibraryRepository externalCqlRepository;

    @Transactional(readOnly = true)
    public List<ArtifactSummary> listByOwner(String ownerUsername) {
        return artifactRepository.findByOwnerUsername(ownerUsername).stream()
                .map(this::entityToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ArtifactResponse> getById(Long id) {
        return artifactRepository.findById(id).map(this::entityToResponse);
    }

    @Transactional
    public ArtifactResponse create(ArtifactRequest request, String ownerUsername) {
        CdsArtifactEntity entity = requestToEntity(request);
        entity.setOwnerUsername(ownerUsername);
        entity = artifactRepository.save(entity);
        log.info("Created CDS artifact: {} v{} by {}", entity.getName(), entity.getVersion(), ownerUsername);
        return entityToResponse(entity);
    }

    @Transactional
    public ArtifactResponse update(Long id, ArtifactRequest request, String currentUser) {
        CdsArtifactEntity entity = artifactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + id));

        checkOwner(entity, currentUser);

        // Update fields
        entity.setName(request.getName());
        if (request.getVersion() != null) entity.setVersion(request.getVersion());
        entity.setDescription(request.getDescription());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getFhirVersion() != null) entity.setFhirVersion(request.getFhirVersion());

        // CPG Metadata
        entity.setUrl(request.getUrl());
        entity.setPublisher(request.getPublisher());
        entity.setPurpose(request.getPurpose());
        entity.setUsageInfo(request.getUsageInfo());
        entity.setCopyright(request.getCopyright());
        if (request.getExperimental() != null) entity.setExperimental(request.getExperimental());
        entity.setApprovalDate(request.getApprovalDate());
        entity.setLastReviewDate(request.getLastReviewDate());
        entity.setEffectivePeriodStart(request.getEffectivePeriodStart());
        entity.setEffectivePeriodEnd(request.getEffectivePeriodEnd());
        entity.setStrengthOfRecommendationMap(request.getStrengthOfRecommendation() != null ? request.getStrengthOfRecommendation() : new LinkedHashMap<>());
        entity.setQualityOfEvidenceMap(request.getQualityOfEvidence() != null ? request.getQualityOfEvidence() : new LinkedHashMap<>());
        entity.setContextList(request.getContext() != null ? request.getContext() : new ArrayList<>());
        entity.setTopicList(request.getTopic() != null ? request.getTopic() : new ArrayList<>());
        entity.setAuthorList(request.getAuthor() != null ? request.getAuthor() : new ArrayList<>());
        entity.setReviewerList(request.getReviewer() != null ? request.getReviewer() : new ArrayList<>());
        entity.setEndorserList(request.getEndorser() != null ? request.getEndorser() : new ArrayList<>());
        entity.setRelatedArtifactList(request.getRelatedArtifact() != null ? request.getRelatedArtifact() : new ArrayList<>());

        // Expression Trees
        if (request.getExpTreeInclude() != null) entity.setExpTreeIncludeMap(request.getExpTreeInclude());
        if (request.getExpTreeExclude() != null) entity.setExpTreeExcludeMap(request.getExpTreeExclude());

        // Linked data
        if (request.getRecommendations() != null) entity.setRecommendationsList(request.getRecommendations());
        if (request.getSubpopulations() != null) entity.setSubpopulationsList(request.getSubpopulations());
        if (request.getBaseElements() != null) entity.setBaseElementsList(request.getBaseElements());
        if (request.getParameters() != null) entity.setParametersList(request.getParameters());
        entity.setErrorStatementMap(request.getErrorStatement());

        // Explicitly serialize transient fields to persistent JSON columns before save.
        // Hibernate dirty-checking does not track @Transient fields, so @PreUpdate may
        // not fire if only transient map/list fields changed.
        entity.serializeAll();
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        entity = artifactRepository.save(entity);
        log.info("Updated CDS artifact: {} (id={})", entity.getName(), entity.getId());
        return entityToResponse(entity);
    }

    @Transactional
    public void delete(Long id, String currentUser) {
        CdsArtifactEntity entity = artifactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + id));
        checkOwner(entity, currentUser);

        externalCqlRepository.deleteByArtifactId(id);
        artifactRepository.deleteById(id);
        log.info("Deleted CDS artifact: {} (id={})", entity.getName(), id);
    }

    @Transactional
    public ArtifactResponse duplicate(Long id, String currentUser) {
        CdsArtifactEntity original = artifactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + id));
        checkOwner(original, currentUser);

        CdsArtifactEntity copy = CdsArtifactEntity.builder()
                .name(original.getName() + " (Copy)")
                .version(original.getVersion())
                .description(original.getDescription())
                .status("draft")
                .fhirVersion(original.getFhirVersion())
                .url(original.getUrl())
                .publisher(original.getPublisher())
                .purpose(original.getPurpose())
                .usageInfo(original.getUsageInfo())
                .copyright(original.getCopyright())
                .experimental(original.getExperimental())
                .approvalDate(null)
                .lastReviewDate(null)
                .effectivePeriodStart(original.getEffectivePeriodStart())
                .effectivePeriodEnd(original.getEffectivePeriodEnd())
                .strengthOfRecommendationMap(new LinkedHashMap<>(original.getStrengthOfRecommendationMap()))
                .qualityOfEvidenceMap(new LinkedHashMap<>(original.getQualityOfEvidenceMap()))
                .contextList(new ArrayList<>(original.getContextList()))
                .topicList(new ArrayList<>(original.getTopicList()))
                .authorList(new ArrayList<>(original.getAuthorList()))
                .reviewerList(new ArrayList<>(original.getReviewerList()))
                .endorserList(new ArrayList<>(original.getEndorserList()))
                .relatedArtifactList(new ArrayList<>(original.getRelatedArtifactList()))
                .expTreeIncludeMap(new LinkedHashMap<>(original.getExpTreeIncludeMap()))
                .expTreeExcludeMap(new LinkedHashMap<>(original.getExpTreeExcludeMap()))
                .recommendationsList(new ArrayList<>(original.getRecommendationsList()))
                .subpopulationsList(new ArrayList<>(original.getSubpopulationsList()))
                .baseElementsList(new ArrayList<>(original.getBaseElementsList()))
                .parametersList(new ArrayList<>(original.getParametersList()))
                .errorStatementMap(original.getErrorStatementMap() != null ? new LinkedHashMap<>(original.getErrorStatementMap()) : null)
                .ownerUsername(currentUser)
                .build();

        copy = artifactRepository.save(copy);
        log.info("Duplicated CDS artifact {} → {} (id={})", original.getId(), copy.getName(), copy.getId());
        return entityToResponse(copy);
    }

    private void checkOwner(CdsArtifactEntity entity, String currentUser) {
        if (!entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can perform this action");
        }
    }

    private ArtifactResponse entityToResponse(CdsArtifactEntity entity) {
        return ArtifactResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .fhirVersion(entity.getFhirVersion())
                .url(entity.getUrl())
                .publisher(entity.getPublisher())
                .purpose(entity.getPurpose())
                .usageInfo(entity.getUsageInfo())
                .copyright(entity.getCopyright())
                .experimental(entity.getExperimental())
                .approvalDate(entity.getApprovalDate())
                .lastReviewDate(entity.getLastReviewDate())
                .effectivePeriodStart(entity.getEffectivePeriodStart())
                .effectivePeriodEnd(entity.getEffectivePeriodEnd())
                .strengthOfRecommendation(entity.getStrengthOfRecommendationMap())
                .qualityOfEvidence(entity.getQualityOfEvidenceMap())
                .context(entity.getContextList())
                .topic(entity.getTopicList())
                .author(entity.getAuthorList())
                .reviewer(entity.getReviewerList())
                .endorser(entity.getEndorserList())
                .relatedArtifact(entity.getRelatedArtifactList())
                .expTreeInclude(entity.getExpTreeIncludeMap())
                .expTreeExclude(entity.getExpTreeExcludeMap())
                .recommendations(entity.getRecommendationsList())
                .subpopulations(entity.getSubpopulationsList())
                .baseElements(entity.getBaseElementsList())
                .parameters(entity.getParametersList())
                .errorStatement(entity.getErrorStatementMap())
                .ownerUsername(entity.getOwnerUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ArtifactSummary entityToSummary(CdsArtifactEntity entity) {
        return ArtifactSummary.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .fhirVersion(entity.getFhirVersion())
                .ownerUsername(entity.getOwnerUsername())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CdsArtifactEntity requestToEntity(ArtifactRequest request) {
        return CdsArtifactEntity.builder()
                .name(request.getName())
                .version(request.getVersion() != null ? request.getVersion() : "1.0.0")
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "draft")
                .fhirVersion(request.getFhirVersion() != null ? request.getFhirVersion() : "4.0.1")
                .url(request.getUrl())
                .publisher(request.getPublisher())
                .purpose(request.getPurpose())
                .usageInfo(request.getUsageInfo())
                .copyright(request.getCopyright())
                .experimental(request.getExperimental() != null ? request.getExperimental() : false)
                .approvalDate(request.getApprovalDate())
                .lastReviewDate(request.getLastReviewDate())
                .effectivePeriodStart(request.getEffectivePeriodStart())
                .effectivePeriodEnd(request.getEffectivePeriodEnd())
                .strengthOfRecommendationMap(request.getStrengthOfRecommendation() != null ? request.getStrengthOfRecommendation() : new LinkedHashMap<>())
                .qualityOfEvidenceMap(request.getQualityOfEvidence() != null ? request.getQualityOfEvidence() : new LinkedHashMap<>())
                .contextList(request.getContext() != null ? request.getContext() : new ArrayList<>())
                .topicList(request.getTopic() != null ? request.getTopic() : new ArrayList<>())
                .authorList(request.getAuthor() != null ? request.getAuthor() : new ArrayList<>())
                .reviewerList(request.getReviewer() != null ? request.getReviewer() : new ArrayList<>())
                .endorserList(request.getEndorser() != null ? request.getEndorser() : new ArrayList<>())
                .relatedArtifactList(request.getRelatedArtifact() != null ? request.getRelatedArtifact() : new ArrayList<>())
                .expTreeIncludeMap(request.getExpTreeInclude() != null ? request.getExpTreeInclude() : new LinkedHashMap<>())
                .expTreeExcludeMap(request.getExpTreeExclude() != null ? request.getExpTreeExclude() : new LinkedHashMap<>())
                .recommendationsList(request.getRecommendations() != null ? request.getRecommendations() : new ArrayList<>())
                .subpopulationsList(request.getSubpopulations() != null ? request.getSubpopulations() : new ArrayList<>())
                .baseElementsList(request.getBaseElements() != null ? request.getBaseElements() : new ArrayList<>())
                .parametersList(request.getParameters() != null ? request.getParameters() : new ArrayList<>())
                .errorStatementMap(request.getErrorStatement())
                .build();
    }
}
