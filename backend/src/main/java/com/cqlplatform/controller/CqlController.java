package com.cqlplatform.controller;

import com.cqlplatform.model.*;
import com.cqlplatform.model.request.AccessUpdateRequest;
import com.cqlplatform.model.request.CqlValidationRequest;
import com.cqlplatform.model.request.LibrarySaveRequest;
import com.cqlplatform.model.request.TransferOwnershipRequest;
import com.cqlplatform.model.request.UsernameRequest;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlRepositoryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DependencyAnalysisService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cql")
@RequiredArgsConstructor
@Tag(name = "CQL", description = "CQL Translation and Execution APIs")
public class CqlController {

    private final CqlTranslationService translationService;
    private final CqlExecutionService executionService;
    private final CqlLibraryService libraryService;
    private final FhirLibraryService fhirLibraryService;
    private final CqlRepositoryService repositoryService;
    private final OwnershipVerifier ownershipVerifier;
    private final DependencyAnalysisService dependencyAnalysisService;

    @PostMapping("/translate")
    @Operation(summary = "Translate CQL to ELM", description = "Translates CQL code to ELM (Expression Logical Model) format")
    public ResponseEntity<CqlTranslationResponse> translate(@Valid @RequestBody CqlTranslationRequest request) {
        CqlTranslationResponse response = translationService.translate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate CQL", description = "Validates CQL code syntax and semantics")
    public ResponseEntity<CqlTranslationResponse> validate(@Valid @RequestBody CqlValidationRequest request) {
        CqlTranslationResponse response = translationService.validate(request.getCql());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute CQL", description = "Executes CQL against a FHIR server")
    public ResponseEntity<CqlExecutionResponse> execute(@Valid @RequestBody CqlExecutionRequest request) {
        InputValidator.requireValidUrl(request.getFhirServerUrl());
        CqlExecutionResponse response = executionService.execute(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/libraries")
    @Operation(summary = "List CQL Libraries", description = "Returns all stored CQL libraries. Supports pagination via page/size params.")
    public ResponseEntity<List<CqlLibrary>> listLibraries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "200") Integer size) {
        List<CqlLibrary> libraries = search != null ?
                libraryService.searchLibraries(search) :
                libraryService.getAllLibraries();
        if (page != null) {
            int start = page * size;
            if (start >= libraries.size()) {
                return ResponseEntity.ok(List.of());
            }
            libraries = libraries.subList(start, Math.min(start + size, libraries.size()));
        }
        return ResponseEntity.ok(libraries);
    }

    @GetMapping("/libraries/{id}")
    @Operation(summary = "Get CQL Library", description = "Returns a specific CQL library by ID")
    public ResponseEntity<CqlLibrary> getLibrary(@PathVariable String id) {
        return libraryService.getLibrary(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/libraries")
    @Operation(summary = "Create CQL Library", description = "Creates a new CQL library")
    public ResponseEntity<CqlLibrary> createLibrary(@Valid @RequestBody LibrarySaveRequest request) {
        CqlLibrary library = libraryService.saveLibrary(request.getCql(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(library);
    }

    @PutMapping("/libraries/{id}")
    @Operation(summary = "Update CQL Library", description = "Updates an existing CQL library")
    public ResponseEntity<CqlLibrary> updateLibrary(
            @PathVariable String id,
            @Valid @RequestBody LibrarySaveRequest request) {
        libraryService.getLibrary(id).ifPresent(lib -> ownershipVerifier.verifyOwnership(lib.getOwnerUsername()));
        CqlLibrary library = libraryService.updateLibrary(id, request.getCql(), request.getDescription());
        return ResponseEntity.ok(library);
    }

    @DeleteMapping("/libraries/{id}")
    @Operation(summary = "Delete CQL Library", description = "Deletes a CQL library")
    public ResponseEntity<Void> deleteLibrary(@PathVariable String id) {
        libraryService.getLibrary(id).ifPresent(lib -> ownershipVerifier.verifyOwnership(lib.getOwnerUsername()));
        libraryService.deleteLibrary(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/libraries/latest/{name}")
    @Operation(summary = "Get Latest Library Version", description = "Returns the latest version of a library by name")
    public ResponseEntity<CqlLibrary> getLatestLibrary(@PathVariable String name) {
        return libraryService.getLatestLibrary(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/libraries/versions/{name}")
    @Operation(summary = "Get Library Versions", description = "Returns all versions of a library sorted descending")
    public ResponseEntity<List<CqlLibrary>> getLibraryVersions(@PathVariable String name) {
        List<CqlLibrary> versions = libraryService.getLibraryVersions(name);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/libraries/metadata")
    @Operation(summary = "Get Library Metadata", description = "Returns lightweight metadata for all libraries")
    public ResponseEntity<List<LibraryMetadataDTO>> getLibrariesMetadata() {
        List<CqlLibrary> libraries = libraryService.getAllLibraries();
        List<LibraryMetadataDTO> metadata = libraries.stream()
                .map(LibraryMetadataDTO::fromLibrary)
                .toList();
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/libraries/{id}/fhir")
    @Operation(summary = "Export as FHIR Library", description = "Exports a CQL library as a FHIR R4 Library resource")
    public ResponseEntity<Object> exportFhirLibrary(@PathVariable String id) {
        return ResponseEntity.ok(fhirLibraryService.exportAsFhirLibrary(id));
    }

    @PostMapping("/libraries/import/fhir")
    @Operation(summary = "Import FHIR Library", description = "Imports a FHIR R4 Library resource containing CQL")
    public ResponseEntity<CqlLibrary> importFhirLibrary(@RequestBody JsonNode fhirLibrary) {
        CqlLibrary library = fhirLibraryService.importFhirLibrary(fhirLibrary);
        return ResponseEntity.ok(library);
    }

    // ===== CQL Repository =====

    @GetMapping("/libraries/repository")
    @Operation(summary = "List Repository Libraries", description = "Lists pre-built CQL library templates available for import")
    public ResponseEntity<List<CqlRepositoryService.RepositoryLibrary>> listRepositoryLibraries() {
        return ResponseEntity.ok(repositoryService.listRepositoryLibraries());
    }

    @PostMapping("/libraries/repository/{name}/import")
    @Operation(summary = "Import Repository Library", description = "Imports a pre-built CQL library template into user's libraries")
    public ResponseEntity<CqlLibrary> importRepositoryLibrary(@PathVariable String name) {
        CqlLibrary library = repositoryService.importFromRepository(name);
        return ResponseEntity.ok(library);
    }

    // ===== Library Version Management =====

    @PostMapping("/libraries/{name}/version")
    @Operation(summary = "Create Library Version", description = "Creates a new version of a library (major/minor/patch)")
    public ResponseEntity<CqlLibrary> createLibraryVersion(
            @PathVariable String name,
            @RequestParam(defaultValue = "minor") String type) {
        CqlLibrary versioned = libraryService.createVersion(name, type);
        return ResponseEntity.ok(versioned);
    }

    @GetMapping("/libraries/{name}/history")
    @Operation(summary = "Library History", description = "Returns all versions of a library as audit history")
    public ResponseEntity<List<CqlLibrary>> getLibraryHistory(@PathVariable String name) {
        List<CqlLibrary> history = libraryService.getHistory(name);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/libraries/compare")
    @Operation(summary = "Compare Library Versions", description = "Returns CQL content of two library versions for diff comparison")
    public ResponseEntity<Map<String, String>> compareLibraryVersions(
            @RequestParam String oldId,
            @RequestParam String newId) {
        Map<String, String> comparison = libraryService.compare(oldId, newId);
        return ResponseEntity.ok(comparison);
    }

    // ===== Library Sharing & Permissions =====

    @PostMapping("/libraries/{id}/share")
    @Operation(summary = "Share Library", description = "Shares a library with another user")
    public ResponseEntity<CqlLibrary> shareLibrary(
            @PathVariable String id,
            @Valid @RequestBody UsernameRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        CqlLibrary library = libraryService.shareLibrary(id, request.getTargetUsername(), currentUser);
        return ResponseEntity.ok(library);
    }

    @PostMapping("/libraries/{id}/unshare")
    @Operation(summary = "Unshare Library", description = "Removes sharing for a user")
    public ResponseEntity<CqlLibrary> unshareLibrary(
            @PathVariable String id,
            @Valid @RequestBody UsernameRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        CqlLibrary library = libraryService.unshareLibrary(id, request.getTargetUsername(), currentUser);
        return ResponseEntity.ok(library);
    }

    @PostMapping("/libraries/{id}/transfer")
    @Operation(summary = "Transfer Library Ownership", description = "Transfers ownership to another user")
    public ResponseEntity<CqlLibrary> transferOwnership(
            @PathVariable String id,
            @Valid @RequestBody TransferOwnershipRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        CqlLibrary library = libraryService.transferOwnership(id, request.getNewOwner(), currentUser);
        return ResponseEntity.ok(library);
    }

    @PutMapping("/libraries/{id}/access")
    @Operation(summary = "Set Access Level", description = "Sets library access level (private/shared/public)")
    public ResponseEntity<CqlLibrary> setAccessLevel(
            @PathVariable String id,
            @Valid @RequestBody AccessUpdateRequest request) {
        String currentUser = ownershipVerifier.getCurrentUsername();
        CqlLibrary library = libraryService.setAccessLevel(id, request.getAccessLevel(), currentUser);
        return ResponseEntity.ok(library);
    }

    @GetMapping("/libraries/owner/{username}")
    @Operation(summary = "Get Libraries by Owner", description = "Returns all libraries owned by a user")
    public ResponseEntity<List<CqlLibrary>> getLibrariesByOwner(@PathVariable String username) {
        List<CqlLibrary> libraries = libraryService.getLibrariesByOwner(username);
        return ResponseEntity.ok(libraries);
    }

    @GetMapping("/libraries/shared/{username}")
    @Operation(summary = "Get Shared Libraries", description = "Returns libraries shared with a user or public")
    public ResponseEntity<List<CqlLibrary>> getSharedLibraries(@PathVariable String username) {
        List<CqlLibrary> libraries = libraryService.getSharedLibraries(username);
        return ResponseEntity.ok(libraries);
    }

    // ===== Dependency Analysis =====

    @GetMapping("/libraries/{id}/dependencies")
    @Operation(summary = "Get Library Dependencies", description = "Returns the full dependency tree for a library")
    public ResponseEntity<List<CqlLibrary>> getDependencies(@PathVariable String id) {
        List<CqlLibrary> dependencies = libraryService.getDependencies(id);
        return ResponseEntity.ok(dependencies);
    }

    @GetMapping("/libraries/dependents/{name}")
    @Operation(summary = "Get Library Dependents", description = "Returns all libraries that depend on the given library")
    public ResponseEntity<List<CqlLibrary>> getDependents(@PathVariable String name) {
        List<CqlLibrary> dependents = libraryService.getDependents(name);
        return ResponseEntity.ok(dependents);
    }

    @GetMapping("/libraries/{id}/dependency-analysis")
    @Operation(summary = "Analyze Dependencies", description = "Analyze library dependencies for version conflicts and mismatches")
    public ResponseEntity<DependencyAnalysisService.DependencyAnalysisResult> analyzeDependencies(@PathVariable String id) {
        DependencyAnalysisService.DependencyAnalysisResult result = dependencyAnalysisService.analyze(id);
        return ResponseEntity.ok(result);
    }
}
