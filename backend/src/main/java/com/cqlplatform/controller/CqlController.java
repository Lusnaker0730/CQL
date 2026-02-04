package com.cqlplatform.controller;

import com.cqlplatform.model.*;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
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
}
