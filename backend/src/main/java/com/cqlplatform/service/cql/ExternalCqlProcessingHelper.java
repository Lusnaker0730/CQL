package com.cqlplatform.service.cql;

import com.cqlplatform.entity.AbstractExternalCqlLibraryEntity;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.util.CqlParsingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helper for processing external CQL libraries.
 * Used by both CDS and eCQM external CQL library services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalCqlProcessingHelper {

    private final CqlTranslationService translationService;

    /**
     * Parse CQL content, translate to ELM, extract metadata, and populate the entity fields.
     * Validates FHIR version consistency against existing versions for the artifact.
     */
    public <E extends AbstractExternalCqlLibraryEntity> void processAndPopulate(
            E entity, String cqlContent, Set<String> existingFhirVersions) {

        String libName = CqlParsingUtils.parseLibraryName(cqlContent);
        String libVersion = CqlParsingUtils.parseLibraryVersion(cqlContent);
        String fhirVersion = CqlParsingUtils.parseFhirVersion(cqlContent);

        // Validate FHIR version consistency
        if (fhirVersion != null && !existingFhirVersions.isEmpty()) {
            for (String existing : existingFhirVersions) {
                if (!existing.equals(fhirVersion)) {
                    throw new IllegalArgumentException(String.format(
                            "FHIR version mismatch: uploaded library uses FHIR %s but existing libraries use FHIR %s. "
                                    + "All external CQL libraries must use the same FHIR version.",
                            fhirVersion, existing));
                }
            }
        }

        // Translate to ELM and extract metadata
        String elmJson = null;
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            CqlTranslationRequest request = new CqlTranslationRequest();
            request.setCql(cqlContent);
            CqlTranslationResponse response = translationService.translate(request);
            if (response.getElmJson() != null) {
                elmJson = response.getElmJson();
            }
            if (response.getMetadata() != null) {
                var meta = response.getMetadata();
                details.put("definitions", meta.getExpressions() != null ? meta.getExpressions() : List.of());
                details.put("parameters", meta.getParameters() != null ? meta.getParameters() : List.of());
                details.put("valueSets", meta.getValueSets() != null ? meta.getValueSets() : List.of());
                details.put("includes", meta.getIncludes() != null ? meta.getIncludes() : List.of());
            }
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                details.put("errors", response.getErrors());
            }
        } catch (Exception e) {
            log.warn("Failed to translate external CQL library {}: {}", libName, e.getMessage());
            details.put("errors", List.of(e.getMessage()));
        }

        entity.setName(libName);
        entity.setVersion(libVersion);
        entity.setFhirVersion(fhirVersion);
        entity.setCqlContent(cqlContent);
        entity.setElmJson(elmJson);
        entity.setDetailsMap(details);
    }

    /** Convert any external CQL library entity to a Map for API response. */
    public Map<String, Object> entityToMap(AbstractExternalCqlLibraryEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("artifactId", entity.getArtifactId());
        map.put("name", entity.getName());
        map.put("version", entity.getVersion());
        map.put("fhirVersion", entity.getFhirVersion());
        map.put("cqlContent", entity.getCqlContent());
        map.put("details", entity.getDetailsMap());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }
}
