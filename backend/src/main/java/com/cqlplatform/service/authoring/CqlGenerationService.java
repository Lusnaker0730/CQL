package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CqlGenerationService {

    private final CdsArtifactRepository artifactRepository;
    private final CqlArtifactBuilder cqlBuilder;
    private final CqlTranslationService translationService;

    @Transactional(readOnly = true)
    public String generateCql(Long artifactId) {
        return generateCql(artifactId, null);
    }

    @Transactional(readOnly = true)
    public String generateCql(Long artifactId, String fhirVersion) {
        CdsArtifactEntity entity = artifactRepository.findById(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));

        String version = fhirVersion != null ? fhirVersion : entity.getFhirVersion();
        if (version == null || version.isEmpty()) {
            version = "R4";
        }

        log.debug("expTreeInclude raw JSON: {}", entity.getExpTreeInclude());
        log.debug("expTreeIncludeMap keys: {}", entity.getExpTreeIncludeMap() != null ? entity.getExpTreeIncludeMap().keySet() : "null");
        log.debug("expTreeIncludeMap childInstances: {}",
                entity.getExpTreeIncludeMap() != null ? entity.getExpTreeIncludeMap().get("childInstances") : "null");

        String cql = cqlBuilder.buildCql(
                entity.getName(),
                entity.getVersion(),
                entity.getExpTreeIncludeMap(),
                entity.getExpTreeExcludeMap(),
                entity.getSubpopulationsList(),
                entity.getBaseElementsList(),
                entity.getParametersList(),
                entity.getErrorStatementMap(),
                entity.getRecommendationsList(),
                version
        );

        log.info("Generated CQL for artifact {} ({}) with FHIR version {}", entity.getName(), artifactId, version);
        return cql;
    }

    @Transactional(readOnly = true)
    public CqlTranslationResponse generateAndTranslate(Long artifactId) {
        String cql = generateCql(artifactId);
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        request.setEnableAnnotations(true);
        request.setEnableLocators(true);
        request.setEnableResultTypes(true);
        return translationService.translate(request);
    }

    @Transactional(readOnly = true)
    public CqlTranslationResponse validateArtifactCql(Long artifactId) {
        String cql = generateCql(artifactId);
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        request.setEnableAnnotations(true);
        request.setEnableLocators(true);
        request.setEnableResultTypes(true);
        return translationService.translate(request);
    }
}
