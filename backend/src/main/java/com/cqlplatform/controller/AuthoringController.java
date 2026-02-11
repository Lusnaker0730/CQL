package com.cqlplatform.controller;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.*;
import com.cqlplatform.service.authoring.ArtifactService;
import com.cqlplatform.service.authoring.ArtifactTestingService;
import com.cqlplatform.service.authoring.CqlGenerationService;
import com.cqlplatform.service.authoring.CqlImportService;
import com.cqlplatform.service.authoring.ExternalCqlLibraryService;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.authoring.QueryBuilderService;
import com.cqlplatform.service.authoring.TemplateService;
import com.cqlplatform.service.cds.CdsHooksService;
import com.cqlplatform.service.cql.CqlLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authoring")
@RequiredArgsConstructor
@Tag(name = "CDS Authoring", description = "CDS Authoring Tool APIs")
public class AuthoringController {

    private final ArtifactService artifactService;
    private final TemplateService templateService;
    private final ModifierService modifierService;
    private final CqlGenerationService cqlGenerationService;
    private final ExternalCqlLibraryService externalCqlLibraryService;
    private final ArtifactTestingService artifactTestingService;
    private final CdsHooksService cdsHooksService;
    private final CqlLibraryService cqlLibraryService;
    private final CqlImportService cqlImportService;
    private final QueryBuilderService queryBuilderService;

    @GetMapping("/artifacts")
    @Operation(summary = "List Artifacts", description = "List the current user's CDS artifacts")
    public ResponseEntity<List<ArtifactSummary>> listArtifacts(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(artifactService.listByOwner(username));
    }

    @GetMapping("/artifacts/{id}")
    @Operation(summary = "Get Artifact", description = "Get a CDS artifact with full expression trees")
    public ResponseEntity<ArtifactResponse> getArtifact(@PathVariable Long id) {
        return artifactService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/artifacts")
    @Operation(summary = "Create Artifact", description = "Create a new CDS artifact")
    public ResponseEntity<ArtifactResponse> createArtifact(
            @Valid @RequestBody ArtifactRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ArtifactResponse created = artifactService.create(request, username);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/artifacts/{id}")
    @Operation(summary = "Update Artifact", description = "Update a CDS artifact (auto-save)")
    public ResponseEntity<ArtifactResponse> updateArtifact(
            @PathVariable Long id,
            @Valid @RequestBody ArtifactRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        ArtifactResponse updated = artifactService.update(id, request, username);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/artifacts/{id}")
    @Operation(summary = "Delete Artifact", description = "Delete a CDS artifact and linked libraries")
    public ResponseEntity<Void> deleteArtifact(
            @PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        artifactService.delete(id, username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/artifacts/{id}/duplicate")
    @Operation(summary = "Duplicate Artifact", description = "Clone a CDS artifact")
    public ResponseEntity<ArtifactResponse> duplicateArtifact(
            @PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        ArtifactResponse duplicated = artifactService.duplicate(id, username);
        return ResponseEntity.ok(duplicated);
    }

    // ===== Templates & Modifiers =====

    @GetMapping("/templates")
    @Operation(summary = "Get Templates", description = "Get all element template categories")
    public ResponseEntity<List<FormTemplateCategory>> getTemplates() {
        return ResponseEntity.ok(templateService.getAllCategories());
    }

    @GetMapping("/modifiers")
    @Operation(summary = "Get Modifiers", description = "Get modifier definitions, optionally filtered by input type")
    public ResponseEntity<List<ModifierDefinition>> getModifiers(
            @RequestParam(required = false) String inputType) {
        if (inputType != null && !inputType.isBlank()) {
            return ResponseEntity.ok(modifierService.getModifiersByInputType(inputType));
        }
        return ResponseEntity.ok(modifierService.getAllModifiers());
    }

    // ===== CQL Generation =====

    @PostMapping("/artifacts/{id}/cql")
    @Operation(summary = "Generate CQL", description = "Generate CQL from artifact expression trees")
    public ResponseEntity<Map<String, String>> generateCql(
            @PathVariable Long id,
            @RequestParam(required = false) String fhirVersion) {
        String cql = cqlGenerationService.generateCql(id, fhirVersion);
        return ResponseEntity.ok(Map.of("cql", cql));
    }

    @PostMapping("/artifacts/{id}/elm")
    @Operation(summary = "Generate ELM", description = "Generate CQL and translate to ELM")
    public ResponseEntity<CqlTranslationResponse> generateElm(@PathVariable Long id) {
        CqlTranslationResponse response = cqlGenerationService.generateAndTranslate(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/artifacts/{id}/validate")
    @Operation(summary = "Validate Artifact CQL", description = "Generate CQL and validate via translation")
    public ResponseEntity<CqlTranslationResponse> validateArtifactCql(@PathVariable Long id) {
        CqlTranslationResponse response = cqlGenerationService.validateArtifactCql(id);
        return ResponseEntity.ok(response);
    }

    // ===== External CQL Libraries =====

    @GetMapping("/artifacts/{id}/external-cql")
    @Operation(summary = "List External CQL", description = "List external CQL libraries for an artifact")
    public ResponseEntity<List<Map<String, Object>>> listExternalCql(@PathVariable Long id) {
        return ResponseEntity.ok(externalCqlLibraryService.listByArtifact(id));
    }

    @GetMapping("/artifacts/{artifactId}/external-cql/{libId}")
    @Operation(summary = "Get External CQL", description = "Get an external CQL library details")
    public ResponseEntity<Map<String, Object>> getExternalCql(
            @PathVariable Long artifactId,
            @PathVariable Long libId) {
        return externalCqlLibraryService.getById(libId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/artifacts/{id}/external-cql/upload")
    @Operation(summary = "Upload External CQL", description = "Upload an external CQL library file")
    public ResponseEntity<Map<String, Object>> uploadExternalCql(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        Map<String, Object> result = externalCqlLibraryService.uploadLibrary(id, file);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/artifacts/{artifactId}/external-cql/{libId}")
    @Operation(summary = "Delete External CQL", description = "Delete an external CQL library")
    public ResponseEntity<Void> deleteExternalCql(
            @PathVariable Long artifactId,
            @PathVariable Long libId) {
        externalCqlLibraryService.deleteLibrary(libId);
        return ResponseEntity.noContent().build();
    }

    // ===== Testing & Deployment =====

    @PostMapping("/artifacts/{id}/test")
    @Operation(summary = "Test Artifact", description = "Execute artifact CQL against patient data")
    public ResponseEntity<Map<String, Object>> testArtifact(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> patientIds = (List<String>) request.getOrDefault("patientIds", List.of());
        String fhirServerUrl = (String) request.get("fhirServerUrl");
        Map<String, Object> result = artifactTestingService.testArtifact(id, patientIds, fhirServerUrl);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/artifacts/{id}/deploy-cds")
    @Operation(summary = "Deploy as CDS Service", description = "Deploy artifact as a CDS Hooks service")
    public ResponseEntity<Map<String, Object>> deployCdsService(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String username = authentication.getName();
        String cql = cqlGenerationService.generateCql(id);

        // Get artifact for metadata
        ArtifactResponse artifact = artifactService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + id));

        String serviceId = request.getOrDefault("serviceId",
                artifact.getName().replaceAll("[^a-zA-Z0-9_-]", "-").toLowerCase());
        String hook = request.getOrDefault("hook", "patient-view");

        com.cqlplatform.model.cds.CdsServiceConfigRequest configRequest =
                com.cqlplatform.model.cds.CdsServiceConfigRequest.builder()
                        .id(serviceId)
                        .hook(hook)
                        .title(artifact.getName())
                        .description(artifact.getDescription())
                        .cqlContent(cql)
                        .defaultIndicator("info")
                        .enabled(true)
                        .build();

        var serviceResponse = cdsHooksService.createService(configRequest, username);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("serviceId", serviceResponse.getId());
        result.put("hook", serviceResponse.getHook());
        result.put("title", serviceResponse.getTitle());
        result.put("message", "Artifact deployed as CDS service successfully");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/artifacts/{id}/save-library")
    @Operation(summary = "Save as Library", description = "Save artifact's generated CQL as a CQL Library")
    public ResponseEntity<Map<String, Object>> saveAsLibrary(
            @PathVariable Long id) {
        String cql = cqlGenerationService.generateCql(id);
        ArtifactResponse artifact = artifactService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + id));

        CqlLibrary library = cqlLibraryService.saveLibrary(cql, artifact.getDescription());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("libraryId", library.getId());
        result.put("name", library.getName());
        result.put("version", library.getVersion());
        result.put("message", "CQL saved as library successfully");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/artifacts/{id}/summary")
    @Operation(summary = "Get Artifact Summary", description = "Get artifact summary data for overview")
    public ResponseEntity<ArtifactResponse> getArtifactSummary(@PathVariable Long id) {
        return artifactService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== CQL Import =====

    @PostMapping("/import-cql")
    @Operation(summary = "Import CQL", description = "Parse CQL code into artifact structure")
    public ResponseEntity<Map<String, Object>> importCql(@RequestBody Map<String, String> request) {
        String cqlContent = request.get("cql");
        if (cqlContent == null || cqlContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "CQL content is required"));
        }
        Map<String, Object> result = cqlImportService.importCql(cqlContent);
        return ResponseEntity.ok(result);
    }

    // ===== Query Builder =====

    @GetMapping("/query-builder/resources")
    @Operation(summary = "Get Resources", description = "Get FHIR R4 resource properties for query builder")
    public ResponseEntity<List<Map<String, Object>>> getQueryBuilderResources() {
        return ResponseEntity.ok(queryBuilderService.getResources());
    }

    @GetMapping("/query-builder/operators")
    @Operation(summary = "Get Operators", description = "Get available query operators")
    public ResponseEntity<List<Map<String, Object>>> getQueryBuilderOperators(
            @RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(queryBuilderService.getOperatorsForType(type));
        }
        return ResponseEntity.ok(queryBuilderService.getOperators());
    }
}
