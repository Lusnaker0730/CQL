package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.exception.CqlGenerationException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.repository.EcqmArtifactRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcqmCqlGenerationService {

    private final EcqmArtifactRepository repository;
    private final EcqmCqlBuilder ecqmCqlBuilder;
    private final CqlTranslationService translationService;

    @Transactional(readOnly = true)
    public CqlBuildResult generateCql(Long artifactId) {
        EcqmArtifactEntity entity = repository.findById(artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("eCQM Artifact", artifactId));

        try {
            CqlBuildResult result = ecqmCqlBuilder.buildEcqmCql(
                    entity.getName(),
                    entity.getVersion(),
                    entity.getScoringType(),
                    entity.getPopulationBasis(),
                    entity.getPopulationGroupsList(),
                    entity.getBaseElementsList(),
                    entity.getParametersList(),
                    entity.getSupplementalDataList(),
                    entity.getStratifiersList(),
                    "R4"
            );
            log.info("Generated eCQM CQL for artifact {} (id={})", entity.getName(), artifactId);
            return result;
        } catch (Exception e) {
            throw new CqlGenerationException(
                    "Failed to generate eCQM CQL for artifact " + artifactId + ": " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public CqlTranslationResponse generateAndTranslate(Long artifactId) {
        CqlBuildResult buildResult = generateCql(artifactId);
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(buildResult.cql());
        request.setEnableAnnotations(true);
        request.setEnableLocators(true);
        request.setEnableResultTypes(true);
        return translationService.translate(request);
    }

    @Transactional(readOnly = true)
    public CqlTranslationResponse validateCql(Long artifactId) {
        CqlBuildResult buildResult = generateCql(artifactId);
        log.info("Validating CQL for artifact {}:\n{}", artifactId, buildResult.cql());
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(buildResult.cql());
        request.setEnableAnnotations(true);
        request.setEnableLocators(true);
        request.setEnableResultTypes(true);
        CqlTranslationResponse response = translationService.translate(request);
        if (!response.isSuccess() && response.getErrors() != null) {
            response.getErrors().forEach(e ->
                log.warn("CQL validation error: Line {}:{} — {}", e.getStartLine(), e.getStartColumn(), e.getMessage()));
        }
        return response;
    }
}
