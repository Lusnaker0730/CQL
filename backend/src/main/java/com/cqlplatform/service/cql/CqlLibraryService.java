package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CqlLibraryService {

    private final CqlTranslationService translationService;
    private final Map<String, CqlLibrary> libraryStore = new ConcurrentHashMap<>();

    public CqlLibrary saveLibrary(String cqlContent, String description) {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cqlContent);
        CqlTranslationResponse response = translationService.translate(request);

        if (!response.isSuccess()) {
            throw new IllegalArgumentException("Invalid CQL: " + response.getErrors());
        }

        String libraryId = response.getMetadata().getLibraryId();
        String version = response.getMetadata().getLibraryVersion();
        String id = libraryId + "-" + version;

        List<String> dependencies = new ArrayList<>();
        if (response.getMetadata().getIncludes() != null) {
            dependencies.addAll(response.getMetadata().getIncludes());
        }

        CqlLibrary library = CqlLibrary.builder()
                .id(id)
                .name(libraryId)
                .version(version)
                .cqlContent(cqlContent)
                .elmJson(response.getElmJson())
                .description(description)
                .status("active")
                .dependencies(dependencies)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        libraryStore.put(id, library);
        log.info("Saved library: {} version {}", libraryId, version);

        return library;
    }

    public Optional<CqlLibrary> getLibrary(String id) {
        return Optional.ofNullable(libraryStore.get(id));
    }

    public Optional<CqlLibrary> getLibraryByNameAndVersion(String name, String version) {
        return libraryStore.values().stream()
                .filter(lib -> lib.getName().equals(name) && lib.getVersion().equals(version))
                .findFirst();
    }

    public List<CqlLibrary> getAllLibraries() {
        return new ArrayList<>(libraryStore.values());
    }

    public List<CqlLibrary> searchLibraries(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return getAllLibraries();
        }

        String lowerSearch = searchTerm.toLowerCase();
        return libraryStore.values().stream()
                .filter(lib -> lib.getName().toLowerCase().contains(lowerSearch) ||
                        (lib.getDescription() != null && lib.getDescription().toLowerCase().contains(lowerSearch)))
                .toList();
    }

    public void deleteLibrary(String id) {
        libraryStore.remove(id);
        log.info("Deleted library: {}", id);
    }

    public CqlLibrary updateLibrary(String id, String cqlContent, String description) {
        CqlLibrary existing = libraryStore.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("Library not found: " + id);
        }

        CqlLibrary updated = saveLibrary(cqlContent, description != null ? description : existing.getDescription());
        libraryStore.remove(id);

        return updated;
    }
}
