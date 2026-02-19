package com.cqlplatform.service.cql;

import com.cqlplatform.entity.CqlLibraryEntity;
import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.repository.CqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CqlLibraryService {

    private final CqlTranslationService translationService;
    private final CqlLibraryRepository libraryRepository;

    @Transactional
    public CqlLibrary saveLibrary(String cqlContent, String description) {
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cqlContent);
        CqlTranslationResponse response = translationService.translate(request);

        if (!response.isSuccess()) {
            throw new IllegalArgumentException("Invalid CQL: " + response.getErrors());
        }

        String libraryId = response.getMetadata().getLibraryId();
        String version = response.getMetadata().getLibraryVersion();

        List<String> dependencies = new ArrayList<>();
        if (response.getMetadata().getIncludes() != null) {
            dependencies.addAll(response.getMetadata().getIncludes());
        }

        // Check if this name+version already exists and update it
        Optional<CqlLibraryEntity> existing = libraryRepository.findByNameAndVersion(libraryId, version);
        CqlLibraryEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setCqlContent(cqlContent);
            entity.setElmJson(response.getElmJson());
            entity.setDescription(description);
            entity.setDependencyList(dependencies);
        } else {
            entity = CqlLibraryEntity.builder()
                    .name(libraryId)
                    .version(version)
                    .cqlContent(cqlContent)
                    .elmJson(response.getElmJson())
                    .description(description)
                    .status("active")
                    .dependencyList(dependencies)
                    .build();
        }

        entity = libraryRepository.save(entity);
        log.info("Saved library: {} version {}", libraryId, version);

        return entityToModel(entity);
    }

    @Transactional(readOnly = true)
    public Optional<CqlLibrary> getLibrary(String id) {
        return parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public Optional<CqlLibrary> getLibraryByNameAndVersion(String name, String version) {
        return libraryRepository.findByNameAndVersion(name, version)
                .map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getAllLibraries() {
        return libraryRepository.findAll().stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> searchLibraries(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return getAllLibraries();
        }
        return libraryRepository
                .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(searchTerm, searchTerm)
                .stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteLibrary(String id) {
        parseId(id).flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .ifPresent(libraryRepository::delete);
        log.info("Deleted library: {}", id);
    }

    @Transactional
    public CqlLibrary updateLibrary(String id, String cqlContent, String description) {
        Optional<CqlLibraryEntity> existing = parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]));

        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Library not found: " + id);
        }

        String existingDesc = existing.get().getDescription();
        CqlLibrary updated = saveLibrary(cqlContent, description != null ? description : existingDesc);

        // If the new translation produced a different name-version, remove old entry
        String newId = updated.getName() + "-" + updated.getVersion();
        if (!newId.equals(id)) {
            parseId(id).flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                    .ifPresent(libraryRepository::delete);
        }

        return updated;
    }

    @Transactional(readOnly = true)
    public Optional<CqlLibrary> getLatestLibrary(String name) {
        List<CqlLibraryEntity> versions = libraryRepository.findByName(name);
        if (versions.isEmpty()) return Optional.empty();

        return versions.stream()
                .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()))
                .map(this::entityToModel);
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getLibraryVersions(String name) {
        return libraryRepository.findByName(name).stream()
                .sorted(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()).reversed())
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    private CqlLibrary entityToModel(CqlLibraryEntity entity) {
        return CqlLibrary.builder()
                .id(entity.getName() + "-" + entity.getVersion())
                .name(entity.getName())
                .version(entity.getVersion())
                .cqlContent(entity.getCqlContent())
                .elmJson(entity.getElmJson())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .dependencies(entity.getDependencyList())
                .ownerUsername(entity.getOwnerUsername())
                .sharedWith(entity.getSharedWithList())
                .accessLevel(entity.getAccessLevel())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ===== Version Management =====

    @Transactional
    public CqlLibrary createVersion(String name, String versionType) {
        List<CqlLibraryEntity> versions = libraryRepository.findByName(name);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("Library not found: " + name);
        }

        CqlLibraryEntity latest = versions.stream()
                .max(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()))
                .orElseThrow();

        String newVersion = bumpVersion(latest.getVersion(), versionType);

        // Check if version already exists
        if (libraryRepository.existsByNameAndVersion(name, newVersion)) {
            throw new IllegalArgumentException("Version already exists: " + name + " v" + newVersion);
        }

        // Set current latest to active
        latest.setStatus("active");
        libraryRepository.save(latest);

        // Create new draft with bumped version
        // Update the CQL content to reflect the new version
        String newCql = latest.getCqlContent().replaceFirst(
                "library\\s+" + java.util.regex.Pattern.quote(name) + "\\s+version\\s+'[^']+'",
                "library " + name + " version '" + newVersion + "'"
        );

        CqlLibraryEntity newEntity = CqlLibraryEntity.builder()
                .name(name)
                .version(newVersion)
                .cqlContent(newCql)
                .elmJson(latest.getElmJson())
                .description(latest.getDescription())
                .status("draft")
                .dependencyList(latest.getDependencyList() != null ? new ArrayList<>(latest.getDependencyList()) : new ArrayList<>())
                .build();

        newEntity = libraryRepository.save(newEntity);
        log.info("Created version {} for library {}", newVersion, name);
        return entityToModel(newEntity);
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getHistory(String name) {
        return libraryRepository.findByName(name).stream()
                .sorted(Comparator.comparing(CqlLibraryEntity::getVersion, new SemanticVersionComparator()).reversed())
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, String> compare(String oldId, String newId) {
        CqlLibrary oldLib = getLibrary(oldId)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + oldId));
        CqlLibrary newLib = getLibrary(newId)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + newId));

        Map<String, String> result = new LinkedHashMap<>();
        result.put("oldCql", oldLib.getCqlContent());
        result.put("newCql", newLib.getCqlContent());
        result.put("oldVersion", oldLib.getVersion());
        result.put("newVersion", newLib.getVersion());
        return result;
    }

    // ===== Sharing & Permissions =====

    @Transactional
    public CqlLibrary shareLibrary(String id, String targetUsername, String currentUser) {
        CqlLibraryEntity entity = parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + id));

        if (entity.getOwnerUsername() != null && !entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can share this library");
        }

        List<String> shared = new ArrayList<>(entity.getSharedWithList());
        if (!shared.contains(targetUsername)) {
            shared.add(targetUsername);
        }
        entity.setSharedWithList(shared);
        if ("private".equals(entity.getAccessLevel())) {
            entity.setAccessLevel("shared");
        }
        entity = libraryRepository.save(entity);
        log.info("Shared library {} with user {}", id, targetUsername);
        return entityToModel(entity);
    }

    @Transactional
    public CqlLibrary unshareLibrary(String id, String targetUsername, String currentUser) {
        CqlLibraryEntity entity = parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + id));

        if (entity.getOwnerUsername() != null && !entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can modify sharing");
        }

        List<String> shared = new ArrayList<>(entity.getSharedWithList());
        shared.remove(targetUsername);
        entity.setSharedWithList(shared);
        if (shared.isEmpty() && "shared".equals(entity.getAccessLevel())) {
            entity.setAccessLevel("private");
        }
        entity = libraryRepository.save(entity);
        return entityToModel(entity);
    }

    @Transactional
    public CqlLibrary transferOwnership(String id, String newOwner, String currentUser) {
        CqlLibraryEntity entity = parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + id));

        if (entity.getOwnerUsername() != null && !entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can transfer ownership");
        }

        entity.setOwnerUsername(newOwner);
        entity = libraryRepository.save(entity);
        log.info("Transferred library {} from {} to {}", id, currentUser, newOwner);
        return entityToModel(entity);
    }

    @Transactional
    public CqlLibrary setAccessLevel(String id, String accessLevel, String currentUser) {
        CqlLibraryEntity entity = parseId(id)
                .flatMap(nv -> libraryRepository.findByNameAndVersion(nv[0], nv[1]))
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + id));

        if (entity.getOwnerUsername() != null && !entity.getOwnerUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Only the owner can change access level");
        }

        entity.setAccessLevel(accessLevel);
        entity = libraryRepository.save(entity);
        return entityToModel(entity);
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getLibrariesByOwner(String ownerUsername) {
        return libraryRepository.findByOwnerUsername(ownerUsername).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getSharedLibraries(String username) {
        return libraryRepository.findAll().stream()
                .filter(e -> e.getSharedWithList().contains(username) || "public".equals(e.getAccessLevel()))
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    // ===== Dependency Analysis =====

    @Transactional(readOnly = true)
    public List<CqlLibrary> getDependents(String libraryName) {
        // Find all libraries that include this library in their dependencies
        return libraryRepository.findByDependenciesContaining(libraryName).stream()
                .map(this::entityToModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CqlLibrary> getDependencies(String id) {
        CqlLibrary library = getLibrary(id)
                .orElseThrow(() -> new IllegalArgumentException("Library not found: " + id));

        List<CqlLibrary> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectDependencies(library, result, visited);
        return result;
    }

    private void collectDependencies(CqlLibrary library, List<CqlLibrary> result, Set<String> visited) {
        if (library.getDependencies() == null) return;
        for (String dep : library.getDependencies()) {
            if (visited.contains(dep)) continue;
            visited.add(dep);
            // dep format is "LibraryName version 'x.y.z'" — extract name and version
            String depName = dep.split("\\s+")[0].replace("\"", "");
            String depVersion = null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("version\\s+'([^']+)'").matcher(dep);
            if (m.find()) {
                depVersion = m.group(1);
            }
            // Try exact version first, fall back to latest
            Optional<CqlLibrary> resolved = Optional.empty();
            if (depVersion != null) {
                resolved = getLibraryByNameAndVersion(depName, depVersion);
            }
            if (resolved.isEmpty()) {
                resolved = getLatestLibrary(depName);
            }
            resolved.ifPresent(depLib -> {
                result.add(depLib);
                collectDependencies(depLib, result, visited);
            });
        }
    }

    private String bumpVersion(String version, String type) {
        String[] parts = version.split("\\.");
        int major = parts.length > 0 ? Integer.parseInt(parts[0].trim()) : 0;
        int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;

        switch (type.toLowerCase()) {
            case "major":
                major++;
                minor = 0;
                patch = 0;
                break;
            case "minor":
                minor++;
                patch = 0;
                break;
            case "patch":
                patch++;
                break;
            default:
                throw new IllegalArgumentException("Invalid version type: " + type + ". Must be major, minor, or patch.");
        }
        return major + "." + minor + "." + patch;
    }

    private Optional<String[]> parseId(String id) {
        if (id == null) return Optional.empty();
        int lastDash = id.lastIndexOf('-');
        if (lastDash <= 0 || lastDash >= id.length() - 1) return Optional.empty();
        return Optional.of(new String[]{id.substring(0, lastDash), id.substring(lastDash + 1)});
    }
}
