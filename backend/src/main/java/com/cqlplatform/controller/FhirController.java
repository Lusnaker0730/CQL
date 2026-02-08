package com.cqlplatform.controller;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.security.InputValidator;
import com.cqlplatform.service.fhir.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.http.HttpStatus;
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
    private final FhirBulkExportService bulkExportService;
    private final FhirValidationService validationService;
    private final VsacService vsacService;
    private final FhirContext fhirContext;

    // ─── Cache Management ────────────────────────────────────────────

    @GetMapping("/cache/stats")
    @Operation(summary = "Cache Statistics", description = "Get cache hit/miss statistics")
    public ResponseEntity<Map<String, Map<String, Object>>> getCacheStats() {
        return ResponseEntity.ok(terminologyService.getCacheStats());
    }

    @DeleteMapping("/cache/{cacheName}")
    @Operation(summary = "Evict Cache", description = "Clear a named cache (ADMIN only)")
    public ResponseEntity<Map<String, String>> evictCache(@PathVariable String cacheName) {
        terminologyService.evictCache(cacheName);
        return ResponseEntity.ok(Map.of("message", "Cache '" + cacheName + "' evicted"));
    }

    // ─── Bulk Data Export ────────────────────────────────────────────

    @PostMapping("/$export")
    @Operation(summary = "Kick Off Bulk Export", description = "Start a bulk data export operation")
    public ResponseEntity<FhirBulkExportService.BulkExportKickOffResult> kickOffExport(
            @RequestParam String fhirServer,
            @RequestParam(defaultValue = "system") String exportType,
            @RequestParam(name = "_outputFormat", required = false) String outputFormat,
            @RequestParam(name = "_since", required = false) String since,
            @RequestParam(name = "_type", required = false) String typeFilter) {

        if (!InputValidator.isValidUrl(fhirServer)) {
            return ResponseEntity.badRequest().build();
        }

        FhirBulkExportService.BulkExportKickOffResult result =
                bulkExportService.kickOffExport(fhirServer, exportType, outputFormat, since, typeFilter);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @GetMapping("/$export-status")
    @Operation(summary = "Poll Export Status", description = "Check the status of a bulk export")
    public ResponseEntity<FhirBulkExportService.BulkExportStatusResult> pollExportStatus(
            @RequestParam String statusUrl) {

        FhirBulkExportService.BulkExportStatusResult result = bulkExportService.pollExportStatus(statusUrl);
        if ("in-progress".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.ok(result);
    }

    // ─── Batch/Transaction ───────────────────────────────────────────

    @PostMapping(value = "/Bundle/$transaction", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute Transaction", description = "Execute a FHIR batch or transaction bundle")
    public ResponseEntity<String> executeTransaction(
            @RequestParam(required = false) String fhirServer,
            @RequestBody String bundleJson) {

        Bundle bundle = (Bundle) fhirContext.newJsonParser().parseResource(bundleJson);
        Bundle result = dataProviderService.executeTransaction(fhirServer, bundle);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(result);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // ─── Resource Validation ─────────────────────────────────────────

    @PostMapping(value = "/$validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validate Resource", description = "Validate a FHIR resource against profiles")
    public ResponseEntity<FhirValidationService.ValidationResult> validateResource(
            @RequestBody String resourceJson) {

        FhirValidationService.ValidationResult result = validationService.validateResource(resourceJson);
        return ResponseEntity.ok(result);
    }

    // ─── Patient Demographics Search ─────────────────────────────────

    @GetMapping("/Patient/$search-by-demographics")
    @Operation(summary = "Search Patients by Demographics", description = "Search for patients by demographic parameters")
    public ResponseEntity<?> searchPatientsByDemographics(
            @RequestParam(required = false) String family,
            @RequestParam(required = false) String given,
            @RequestParam(required = false) String birthdate,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) String fhirServer) {

        if (!InputValidator.isValidNameParam(family)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid family name parameter"));
        }
        if (!InputValidator.isValidNameParam(given)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid given name parameter"));
        }
        if (!InputValidator.isValidDateParam(birthdate)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid birthdate parameter"));
        }
        if (!InputValidator.isValidUrl(fhirServer)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid FHIR server URL"));
        }

        Bundle bundle = dataProviderService.searchPatientsByDemographics(fhirServer, family, given, birthdate, identifier);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // ─── VSAC Integration ────────────────────────────────────────────

    @GetMapping("/vsac/ValueSet")
    @Operation(summary = "Search VSAC ValueSets", description = "Search for ValueSets in VSAC by title")
    public ResponseEntity<List<Map<String, String>>> searchVsacValueSets(
            @RequestParam(required = false) String title) {

        List<ValueSet> valueSets = vsacService.searchValueSets(title != null ? title : "");
        List<Map<String, String>> results = valueSets.stream()
                .map(vs -> Map.of(
                        "id", vs.getId() != null ? vs.getId() : "",
                        "url", vs.getUrl() != null ? vs.getUrl() : "",
                        "name", vs.getName() != null ? vs.getName() : "",
                        "title", vs.getTitle() != null ? vs.getTitle() : ""
                ))
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/vsac/ValueSet/{oid}")
    @Operation(summary = "Get VSAC ValueSet", description = "Fetch a ValueSet from VSAC by OID")
    public ResponseEntity<String> getVsacValueSet(@PathVariable String oid) {
        if (!InputValidator.isValidResourceId(oid)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid OID\"}");
        }

        ValueSet valueSet = vsacService.getValueSetByOid(oid);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(valueSet);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/vsac/ValueSet/{oid}/$expand")
    @Operation(summary = "Expand VSAC ValueSet", description = "Expand a ValueSet from VSAC by OID")
    public ResponseEntity<String> expandVsacValueSet(@PathVariable String oid) {
        if (!InputValidator.isValidResourceId(oid)) {
            return ResponseEntity.badRequest().body("{\"error\":\"Invalid OID\"}");
        }

        ValueSet expanded = vsacService.expandValueSetByOid(oid);
        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(expanded);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // ─── Existing CRUD + Terminology Endpoints ───────────────────────

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
    @Operation(summary = "Expand ValueSet", description = "Expand a ValueSet (auto-routes VSAC URLs to VSAC service)")
    public ResponseEntity<String> expandValueSet(
            @RequestParam String url,
            @RequestParam(required = false) String filter) {

        ValueSet expanded;
        // Detect VSAC URLs and route to VsacService which has proper auth
        if (url.contains("cts.nlm.nih.gov/fhir/ValueSet/")) {
            String oid = url.substring(url.lastIndexOf("/") + 1);
            expanded = vsacService.expandValueSetByOid(oid);
        } else {
            expanded = terminologyService.expandValueSet(url, filter);
        }
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
