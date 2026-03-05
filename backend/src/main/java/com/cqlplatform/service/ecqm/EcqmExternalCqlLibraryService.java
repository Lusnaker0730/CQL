package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmExternalCqlLibraryEntity;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.repository.EcqmExternalCqlLibraryRepository;
import com.cqlplatform.service.cql.CqlTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EcqmExternalCqlLibraryService {

    private final EcqmExternalCqlLibraryRepository repository;
    private final CqlTranslationService translationService;

    private static final Pattern LIBRARY_PATTERN = Pattern
            .compile("library\\s+\"?([\\w_]+)\"?(?:\\s+version\\s+'([^']*)')?");
    private static final Pattern FHIR_VERSION_PATTERN = Pattern.compile("using\\s+FHIR\\s+version\\s+'([^']*)'");

    public List<Map<String, Object>> listByArtifact(Long artifactId) {
        return repository.findByArtifactId(artifactId).stream()
                .map(this::entityToMap)
                .toList();
    }

    public Optional<Map<String, Object>> getByIdAndArtifactId(Long id, Long artifactId) {
        return repository.findByIdAndArtifactId(id, artifactId).map(this::entityToMap);
    }

    @Transactional
    public Map<String, Object> uploadLibrary(Long artifactId, MultipartFile file) throws IOException {
        return processAndSave(artifactId, new String(file.getBytes()));
    }

    @Transactional
    public Map<String, Object> uploadLibraryFromContent(Long artifactId, String cqlContent) {
        return processAndSave(artifactId, cqlContent);
    }

    @Transactional
    public boolean deleteLibraryIfOwnedByArtifact(Long id, Long artifactId) {
        return repository.findByIdAndArtifactId(id, artifactId)
                .map(entity -> { repository.delete(entity); return true; })
                .orElse(false);
    }

    private Map<String, Object> processAndSave(Long artifactId, String cqlContent) {
        String libName = "Unknown";
        String libVersion = null;
        String fhirVersion = null;

        Matcher libMatcher = LIBRARY_PATTERN.matcher(cqlContent);
        if (libMatcher.find()) {
            libName = libMatcher.group(1);
            libVersion = libMatcher.group(2);
        }

        Matcher fhirMatcher = FHIR_VERSION_PATTERN.matcher(cqlContent);
        if (fhirMatcher.find()) {
            fhirVersion = fhirMatcher.group(1);
        }

        // Validate FHIR version consistency
        List<EcqmExternalCqlLibraryEntity> existingLibs = repository.findByArtifactId(artifactId);
        if (fhirVersion != null && !existingLibs.isEmpty()) {
            for (EcqmExternalCqlLibraryEntity existing : existingLibs) {
                if (existing.getFhirVersion() != null && !existing.getFhirVersion().equals(fhirVersion)) {
                    throw new IllegalArgumentException(String.format(
                            "FHIR version mismatch: uploaded library uses FHIR %s but existing library '%s' uses FHIR %s. "
                                    + "All external CQL libraries must use the same FHIR version.",
                            fhirVersion, existing.getName(), existing.getFhirVersion()));
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
            log.warn("Failed to translate eCQM external CQL library {}: {}", libName, e.getMessage());
            details.put("errors", List.of(e.getMessage()));
        }

        EcqmExternalCqlLibraryEntity entity = EcqmExternalCqlLibraryEntity.builder()
                .artifactId(artifactId)
                .name(libName)
                .version(libVersion)
                .fhirVersion(fhirVersion)
                .cqlContent(cqlContent)
                .elmJson(elmJson)
                .detailsMap(details)
                .build();

        entity = repository.save(entity);
        return entityToMap(entity);
    }

    private Map<String, Object> entityToMap(EcqmExternalCqlLibraryEntity entity) {
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
