package com.cqlplatform.controller;

import com.cqlplatform.model.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/translate")
    @Operation(summary = "Translate CQL to ELM", description = "Translates CQL code to ELM (Expression Logical Model) format")
    public ResponseEntity<CqlTranslationResponse> translate(@Valid @RequestBody CqlTranslationRequest request) {
        CqlTranslationResponse response = translationService.translate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate CQL", description = "Validates CQL code syntax and semantics")
    public ResponseEntity<CqlTranslationResponse> validate(@RequestBody Map<String, String> request) {
        String cql = request.get("cql");
        CqlTranslationResponse response = translationService.validate(cql);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute CQL", description = "Executes CQL against a FHIR server")
    public ResponseEntity<CqlExecutionResponse> execute(@Valid @RequestBody CqlExecutionRequest request) {
        CqlExecutionResponse response = executionService.execute(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/libraries")
    @Operation(summary = "List CQL Libraries", description = "Returns all stored CQL libraries")
    public ResponseEntity<List<CqlLibrary>> listLibraries(
            @RequestParam(required = false) String search) {
        List<CqlLibrary> libraries = search != null ?
                libraryService.searchLibraries(search) :
                libraryService.getAllLibraries();
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
    public ResponseEntity<CqlLibrary> createLibrary(@RequestBody Map<String, String> request) {
        String cql = request.get("cql");
        String description = request.get("description");
        CqlLibrary library = libraryService.saveLibrary(cql, description);
        return ResponseEntity.ok(library);
    }

    @PutMapping("/libraries/{id}")
    @Operation(summary = "Update CQL Library", description = "Updates an existing CQL library")
    public ResponseEntity<CqlLibrary> updateLibrary(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String cql = request.get("cql");
        String description = request.get("description");
        CqlLibrary library = libraryService.updateLibrary(id, cql, description);
        return ResponseEntity.ok(library);
    }

    @DeleteMapping("/libraries/{id}")
    @Operation(summary = "Delete CQL Library", description = "Deletes a CQL library")
    public ResponseEntity<Void> deleteLibrary(@PathVariable String id) {
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
            @RequestBody Map<String, String> request) {
        String targetUsername = request.get("targetUsername");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        CqlLibrary library = libraryService.shareLibrary(id, targetUsername, currentUser);
        return ResponseEntity.ok(library);
    }

    @PostMapping("/libraries/{id}/unshare")
    @Operation(summary = "Unshare Library", description = "Removes sharing for a user")
    public ResponseEntity<CqlLibrary> unshareLibrary(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String targetUsername = request.get("targetUsername");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        CqlLibrary library = libraryService.unshareLibrary(id, targetUsername, currentUser);
        return ResponseEntity.ok(library);
    }

    @PostMapping("/libraries/{id}/transfer")
    @Operation(summary = "Transfer Library Ownership", description = "Transfers ownership to another user")
    public ResponseEntity<CqlLibrary> transferOwnership(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String newOwner = request.get("newOwner");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        CqlLibrary library = libraryService.transferOwnership(id, newOwner, currentUser);
        return ResponseEntity.ok(library);
    }

    @PutMapping("/libraries/{id}/access")
    @Operation(summary = "Set Access Level", description = "Sets library access level (private/shared/public)")
    public ResponseEntity<CqlLibrary> setAccessLevel(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String accessLevel = request.get("accessLevel");
        String currentUser = request.getOrDefault("currentUser", "anonymous");
        CqlLibrary library = libraryService.setAccessLevel(id, accessLevel, currentUser);
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
}
