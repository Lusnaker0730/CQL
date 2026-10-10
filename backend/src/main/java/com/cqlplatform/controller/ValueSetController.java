package com.cqlplatform.controller;

import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.service.terminology.PlatformValueSetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PAT-230 — value sets owned by this installation. Everything is scoped to the caller's tenant. */
@RestController
@RequestMapping("/api/value-sets")
@RequiredArgsConstructor
@Tag(name = "Value Sets", description = "Author, version, import and export this installation's own value sets")
public class ValueSetController {

    private final PlatformValueSetService valueSetService;

    @Data
    public static class NewVersionRequest {
        @NotBlank
        private String version;
    }

    @GetMapping
    @Operation(summary = "List value sets", description = "Every version of every value set of the caller's tenant (without the codes)")
    public ResponseEntity<List<PlatformValueSet>> list(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(valueSetService.list(search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a value set version, with its codes")
    public ResponseEntity<PlatformValueSet> get(@PathVariable Long id) {
        return ResponseEntity.ok(valueSetService.get(id));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "List the versions that share this value set's URL")
    public ResponseEntity<List<PlatformValueSet>> versions(@PathVariable Long id) {
        return ResponseEntity.ok(valueSetService.versions(id));
    }

    @PostMapping
    @Operation(summary = "Create a value set", description = "Starts as a draft. A blank url is derived from the name and FHIR_CANONICAL_BASE.")
    public ResponseEntity<PlatformValueSet> create(@RequestBody PlatformValueSet request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(valueSetService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a draft", description = "Active and retired versions are frozen: create a new version instead")
    public ResponseEntity<PlatformValueSet> update(@PathVariable Long id, @RequestBody PlatformValueSet request) {
        return ResponseEntity.ok(valueSetService.update(id, request));
    }

    @PostMapping("/{id}/versions")
    @Operation(summary = "Create a new draft version from this one")
    public ResponseEntity<PlatformValueSet> createVersion(@PathVariable Long id,
                                                          @jakarta.validation.Valid @RequestBody NewVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(valueSetService.createVersion(id, request.getVersion()));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a draft", description = "Freezes the codes. Unversioned CQL references resolve to the newest active version.")
    public ResponseEntity<PlatformValueSet> activate(@PathVariable Long id) {
        return ResponseEntity.ok(valueSetService.activate(id));
    }

    @PostMapping("/{id}/retire")
    @Operation(summary = "Retire an active version")
    public ResponseEntity<PlatformValueSet> retire(@PathVariable Long id) {
        return ResponseEntity.ok(valueSetService.retire(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        valueSetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/fhir")
    @Operation(summary = "Export as a FHIR R4 ValueSet")
    public ResponseEntity<ObjectNode> exportFhir(@PathVariable Long id) {
        return ResponseEntity.ok(valueSetService.exportFhir(id));
    }

    @PostMapping("/import/fhir")
    @Operation(summary = "Import a FHIR R4 ValueSet", description = "Must carry its codes (compose concepts or an expansion). Arrives as a draft.")
    public ResponseEntity<PlatformValueSet> importFhir(@RequestBody JsonNode valueSet) {
        return ResponseEntity.status(HttpStatus.CREATED).body(valueSetService.importFhir(valueSet));
    }
}
