package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsExternalCqlLibraryEntity;
import com.cqlplatform.repository.CdsExternalCqlLibraryRepository;
import com.cqlplatform.service.cql.ExternalCqlProcessingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for managing external CQL libraries attached to CDS artifacts.
 * Handles upload, parsing, ELM translation, and metadata extraction.
 */
@Service
@RequiredArgsConstructor
public class ExternalCqlLibraryService {

    private final CdsExternalCqlLibraryRepository repository;
    private final ExternalCqlProcessingHelper processingHelper;

    public List<Map<String, Object>> listByArtifact(Long artifactId) {
        return repository.findByArtifactId(artifactId).stream()
                .map(processingHelper::entityToMap)
                .toList();
    }

    public Optional<Map<String, Object>> getById(Long id) {
        return repository.findById(id).map(processingHelper::entityToMap);
    }

    public Optional<Map<String, Object>> getByIdAndArtifactId(Long id, Long artifactId) {
        return repository.findByIdAndArtifactId(id, artifactId).map(processingHelper::entityToMap);
    }

    @Transactional
    public Map<String, Object> uploadLibrary(Long artifactId, MultipartFile file) throws IOException {
        return processAndSave(artifactId, new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    @Transactional
    public Map<String, Object> uploadLibraryFromContent(Long artifactId, String cqlContent) {
        return processAndSave(artifactId, cqlContent);
    }

    @Transactional
    public void deleteLibrary(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public boolean deleteLibraryIfOwnedByArtifact(Long id, Long artifactId) {
        return repository.findByIdAndArtifactId(id, artifactId)
                .map(entity -> { repository.delete(entity); return true; })
                .orElse(false);
    }

    private Map<String, Object> processAndSave(Long artifactId, String cqlContent) {
        Set<String> existingVersions = repository.findDistinctFhirVersionsByArtifactId(artifactId);

        CdsExternalCqlLibraryEntity entity = CdsExternalCqlLibraryEntity.builder()
                .artifactId(artifactId)
                .build();
        processingHelper.processAndPopulate(entity, cqlContent, existingVersions);

        entity = repository.save(entity);
        return processingHelper.entityToMap(entity);
    }
}
