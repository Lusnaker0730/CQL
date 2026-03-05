package com.cqlplatform.controller;

import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.authoring.FormTemplateCategory;
import com.cqlplatform.model.authoring.ModifierDefinition;
import com.cqlplatform.model.ecqm.*;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.authoring.TemplateService;
import com.cqlplatform.service.ecqm.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecqm")
@RequiredArgsConstructor
@Tag(name = "eCQM Authoring", description = "eCQM Visual CQL Builder APIs")
public class EcqmController {

    private final EcqmArtifactService artifactService;
    private final EcqmCqlGenerationService cqlGenerationService;
    private final EcqmPublishService publishService;
    private final EcqmExpressionTreeValidator validator;
    private final TemplateService templateService;
    private final ModifierService modifierService;
    private final EcqmExternalCqlLibraryService externalCqlService;
    private final OwnershipVerifier ownershipVerifier;

    private void verifyOwnership(Long artifactId) {
        EcqmArtifactResponse artifact = artifactService.getById(artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("eCQM Artifact", artifactId));
        ownershipVerifier.verifyOwnership(artifact.getOwnerUsername());
    }

    // ===== CRUD =====

    @GetMapping("/artifacts")
    @Operation(summary = "List eCQM Artifacts", description = "List current user's eCQM artifacts")
    public ResponseEntity<List<EcqmArtifactSummary>> listArtifacts(Authentication authentication) {
        return ResponseEntity.ok(artifactService.listByOwner(authentication.getName()));
    }

    @GetMapping("/artifacts/{id}")
    @Operation(summary = "Get eCQM Artifact", description = "Get full eCQM artifact with population groups")
    public ResponseEntity<EcqmArtifactResponse> getArtifact(@PathVariable Long id) {
        verifyOwnership(id);
        return artifactService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/artifacts")
    @Operation(summary = "Create eCQM Artifact")
    public ResponseEntity<EcqmArtifactResponse> createArtifact(
            @Valid @RequestBody EcqmArtifactRequest request,
            Authentication authentication) {
        validator.validate(request);
        EcqmArtifactResponse created = artifactService.create(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/artifacts/{id}")
    @Operation(summary = "Update eCQM Artifact")
    public ResponseEntity<EcqmArtifactResponse> updateArtifact(
            @PathVariable Long id,
            @Valid @RequestBody EcqmArtifactRequest request,
            Authentication authentication) {
        validator.validate(request);
        EcqmArtifactResponse updated = artifactService.update(id, request, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/artifacts/{id}")
    @Operation(summary = "Delete eCQM Artifact")
    public ResponseEntity<Void> deleteArtifact(
            @PathVariable Long id, Authentication authentication) {
        artifactService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/artifacts/{id}/duplicate")
    @Operation(summary = "Duplicate eCQM Artifact")
    public ResponseEntity<EcqmArtifactResponse> duplicateArtifact(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(artifactService.duplicate(id, authentication.getName()));
    }

    // ===== CQL Generation =====

    @PostMapping("/artifacts/{id}/cql")
    @Operation(summary = "Generate eCQM CQL", description = "Generate CQL from eCQM artifact")
    public ResponseEntity<Map<String, Object>> generateCql(@PathVariable Long id) {
        verifyOwnership(id);
        CqlBuildResult result = cqlGenerationService.generateCql(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cql", result.cql());
        if (result.hasWarnings()) {
            body.put("warnings", result.warnings());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/artifacts/{id}/elm")
    @Operation(summary = "Generate ELM", description = "Generate CQL and translate to ELM")
    public ResponseEntity<CqlTranslationResponse> generateElm(@PathVariable Long id) {
        verifyOwnership(id);
        return ResponseEntity.ok(cqlGenerationService.generateAndTranslate(id));
    }

    @PostMapping("/artifacts/{id}/validate")
    @Operation(summary = "Validate eCQM CQL")
    public ResponseEntity<CqlTranslationResponse> validateCql(@PathVariable Long id) {
        verifyOwnership(id);
        return ResponseEntity.ok(cqlGenerationService.validateCql(id));
    }

    @PostMapping("/artifacts/{id}/publish")
    @Operation(summary = "Publish to MeasureDefinition",
            description = "Generate CQL and create/update MeasureDefinition for evaluation")
    public ResponseEntity<PublishResult> publish(
            @PathVariable Long id, Authentication authentication) {
        verifyOwnership(id);
        return ResponseEntity.ok(publishService.publish(id, authentication.getName()));
    }

    // ===== Templates & Modifiers (delegated) =====

    @GetMapping("/templates")
    @Operation(summary = "Get Element Templates")
    public ResponseEntity<List<FormTemplateCategory>> getTemplates() {
        return ResponseEntity.ok(templateService.getAllCategories());
    }

    @GetMapping("/modifiers")
    @Operation(summary = "Get Modifiers")
    public ResponseEntity<List<ModifierDefinition>> getModifiers(
            @RequestParam(required = false) String inputType) {
        if (inputType != null && !inputType.isBlank()) {
            return ResponseEntity.ok(modifierService.getModifiersByInputType(inputType));
        }
        return ResponseEntity.ok(modifierService.getAllModifiers());
    }

    // ===== External CQL Libraries =====

    @GetMapping("/artifacts/{id}/external-cql")
    @Operation(summary = "List external CQL libraries for eCQM artifact")
    public ResponseEntity<List<Map<String, Object>>> listExternalCql(@PathVariable Long id) {
        verifyOwnership(id);
        return ResponseEntity.ok(externalCqlService.listByArtifact(id));
    }

    @GetMapping("/artifacts/{artifactId}/external-cql/{libId}")
    @Operation(summary = "Get external CQL library")
    public ResponseEntity<Map<String, Object>> getExternalCql(
            @PathVariable Long artifactId, @PathVariable Long libId) {
        verifyOwnership(artifactId);
        return externalCqlService.getByIdAndArtifactId(libId, artifactId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/artifacts/{id}/external-cql/upload")
    @Operation(summary = "Upload external CQL library (.cql file)")
    public ResponseEntity<Map<String, Object>> uploadExternalCql(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) throws Exception {
        verifyOwnership(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(externalCqlService.uploadLibrary(id, file));
    }

    @PostMapping("/artifacts/{id}/external-cql/content")
    @Operation(summary = "Upload external CQL from raw content")
    public ResponseEntity<Map<String, Object>> uploadExternalCqlContent(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        verifyOwnership(id);
        String cqlContent = body.get("cqlContent");
        if (cqlContent == null || cqlContent.isBlank()) {
            throw new IllegalArgumentException("cqlContent is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(externalCqlService.uploadLibraryFromContent(id, cqlContent));
    }

    @DeleteMapping("/artifacts/{artifactId}/external-cql/{libId}")
    @Operation(summary = "Delete external CQL library")
    public ResponseEntity<Void> deleteExternalCql(
            @PathVariable Long artifactId, @PathVariable Long libId) {
        verifyOwnership(artifactId);
        if (externalCqlService.deleteLibraryIfOwnedByArtifact(libId, artifactId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ===== Scoring Types =====

    @GetMapping("/scoring-types")
    @Operation(summary = "Get Scoring Types", description = "Get scoring type configurations with required populations")
    public ResponseEntity<Map<String, Object>> getScoringTypes() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scoringTypes", List.of(
                Map.of("id", "proportion", "label", "Proportion",
                        "required", EcqmConstants.REQUIRED_POPULATIONS.get("proportion"),
                        "optional", EcqmConstants.OPTIONAL_POPULATIONS.get("proportion")),
                Map.of("id", "ratio", "label", "Ratio",
                        "required", EcqmConstants.REQUIRED_POPULATIONS.get("ratio"),
                        "optional", EcqmConstants.OPTIONAL_POPULATIONS.get("ratio")),
                Map.of("id", "continuous-variable", "label", "Continuous Variable",
                        "required", EcqmConstants.REQUIRED_POPULATIONS.get("continuous-variable"),
                        "optional", EcqmConstants.OPTIONAL_POPULATIONS.get("continuous-variable")),
                Map.of("id", "cohort", "label", "Cohort",
                        "required", EcqmConstants.REQUIRED_POPULATIONS.get("cohort"),
                        "optional", EcqmConstants.OPTIONAL_POPULATIONS.get("cohort"))
        ));
        result.put("populationBasisOptions", EcqmConstants.POPULATION_BASIS_OPTIONS);
        result.put("aggregateMethods", EcqmConstants.AGGREGATE_METHODS);
        return ResponseEntity.ok(result);
    }
}
