package com.cqlplatform.controller;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fhir")
@RequiredArgsConstructor
@Tag(name = "FHIR", description = "FHIR Resource APIs")
public class FhirController {

    private final FhirDataProviderService dataProviderService;
    private final FhirTerminologyService terminologyService;
    private final FhirContext fhirContext;

    @GetMapping("/{resourceType}")
    @Operation(summary = "Search Resources", description = "Search FHIR resources")
    public ResponseEntity<String> searchResources(
            @PathVariable String resourceType,
            @RequestParam(required = false) String fhirServer,
            @RequestParam(required = false) String params) {

        if (!InputValidator.isValidFhirResourceType(resourceType)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid FHIR resource type: " + resourceType + "\"}");
        }
        if (!InputValidator.isValidSearchParams(params)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid search parameters\"}");
        }
        if (!InputValidator.isValidUrl(fhirServer)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid FHIR server URL\"}");
        }

        Bundle bundle = dataProviderService.searchResources(fhirServer, resourceType, params);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/{resourceType}/{id}")
    @Operation(summary = "Read Resource", description = "Read a specific FHIR resource")
    public ResponseEntity<String> readResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam(required = false) String fhirServer) {

        if (!InputValidator.isValidFhirResourceType(resourceType)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid FHIR resource type\"}");
        }
        if (!InputValidator.isValidResourceId(id)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid resource ID\"}");
        }

        Resource resource = dataProviderService.getResource(fhirServer, resourceType, id);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping(value = "/{resourceType}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create Resource", description = "Create a new FHIR resource")
    public ResponseEntity<String> createResource(
            @PathVariable String resourceType,
            @RequestParam(required = false) String fhirServer,
            @RequestBody String resourceJson) {

        Resource resource = (Resource) fhirContext.newJsonParser().parseResource(resourceJson);
        Resource created = dataProviderService.createResource(fhirServer, resource);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(created);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PutMapping(value = "/{resourceType}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update Resource", description = "Update an existing FHIR resource")
    public ResponseEntity<String> updateResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam(required = false) String fhirServer,
            @RequestBody String resourceJson) {

        Resource resource = (Resource) fhirContext.newJsonParser().parseResource(resourceJson);
        resource.setId(id);
        Resource updated = dataProviderService.updateResource(fhirServer, resource);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(updated);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @DeleteMapping("/{resourceType}/{id}")
    @Operation(summary = "Delete Resource", description = "Delete a FHIR resource")
    public ResponseEntity<Void> deleteResource(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestParam(required = false) String fhirServer) {

        dataProviderService.deleteResource(fhirServer, resourceType, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ValueSet/$expand")
    @Operation(summary = "Expand ValueSet", description = "Expand a ValueSet")
    public ResponseEntity<String> expandValueSet(
            @RequestParam String url,
            @RequestParam(required = false) String filter) {

        ValueSet expanded = terminologyService.expandValueSet(url, filter);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expanded);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/CodeSystem/$validate-code")
    @Operation(summary = "Validate Code", description = "Validate a code against a ValueSet")
    public ResponseEntity<Map<String, Object>> validateCode(
            @RequestParam String system,
            @RequestParam String code,
            @RequestParam String valueSet) {

        boolean valid = terminologyService.validateCode(system, code, valueSet);
        return ResponseEntity.ok(Map.of(
                "result", valid,
                "system", system,
                "code", code,
                "valueSet", valueSet
        ));
    }

    @GetMapping("/CodeSystem/$lookup")
    @Operation(summary = "Lookup Code", description = "Lookup information about a code")
    public ResponseEntity<FhirTerminologyService.CodeLookupResult> lookupCode(
            @RequestParam String system,
            @RequestParam String code) {

        FhirTerminologyService.CodeLookupResult result = terminologyService.lookupCode(system, code);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ValueSet")
    @Operation(summary = "Search ValueSets", description = "Search for ValueSets")
    public ResponseEntity<List<Map<String, String>>> searchValueSets(
            @RequestParam(required = false) String title) {

        List<ValueSet> valueSets = terminologyService.searchValueSets(title != null ? title : "");
        List<Map<String, String>> results = valueSets.stream()
                .map(vs -> Map.of(
                        "id", vs.getId(),
                        "url", vs.getUrl() != null ? vs.getUrl() : "",
                        "name", vs.getName() != null ? vs.getName() : "",
                        "title", vs.getTitle() != null ? vs.getTitle() : ""
                ))
                .toList();
        return ResponseEntity.ok(results);
    }
}
