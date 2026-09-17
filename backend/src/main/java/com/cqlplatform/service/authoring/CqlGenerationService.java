package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.exception.CqlGenerationException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
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
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
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
    public String generateCql(Long artifactId) {
        return generateCql(artifactId, null);
    }

    @Transactional(readOnly = true)
    public String generateCql(Long artifactId, String fhirVersion) {
        return doBuildCql(artifactId, fhirVersion).cql();
    }

    @Transactional(readOnly = true)
    public CqlBuildResult generateCqlWithWarnings(Long artifactId, String fhirVersion) {
        return doBuildCql(artifactId, fhirVersion);
    }

    private CqlBuildResult doBuildCql(Long artifactId, String fhirVersion) {
        // Tenant-scoped (BUG-134): generating CQL reads the artifact's full clinical logic, so
        // an artifact in another tenant must read as not-found here too — not just via
        // ArtifactService.getById.
        CdsArtifactEntity entity = artifactRepository.findByIdAndTenantId(artifactId, effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));

        // Authoring currently supports R4 only — ignore fhirVersion override
        String version = "R4";

        log.debug("expTreeInclude raw JSON: {}", entity.getExpTreeInclude());
        log.debug("expTreeIncludeMap keys: {}", entity.getExpTreeIncludeMap() != null ? entity.getExpTreeIncludeMap().keySet() : "null");
        log.debug("expTreeIncludeMap childInstances: {}",
                entity.getExpTreeIncludeMap() != null ? entity.getExpTreeIncludeMap().get("childInstances") : "null");

        CqlBuildResult result;
        try {
            result = cqlBuilder.buildCql(
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
        } catch (CqlGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new CqlGenerationException(
                    "Failed to generate CQL for artifact " + artifactId + ": " + e.getMessage(), e);
        }

        log.info("Generated CQL for artifact {} ({}) with FHIR version {}", entity.getName(), artifactId, version);
        return result;
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
