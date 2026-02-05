package com.cqlplatform.controller;

import com.cqlplatform.model.cds.CdsServiceConfigRequest;
import com.cqlplatform.model.cds.CdsServiceConfigResponse;
import com.cqlplatform.service.cds.CdsHooksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cds/services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CDS Service Configuration", description = "APIs for managing CDS Hooks services")
public class CdsServiceConfigController {

    private final CdsHooksService cdsHooksService;

    @GetMapping
    @Operation(summary = "List all CDS services", description = "Returns all configured CDS services")
    public ResponseEntity<List<CdsServiceConfigResponse>> getAllServices() {
        log.info("Listing all CDS services");
        return ResponseEntity.ok(cdsHooksService.getAllServices());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a CDS service", description = "Returns a specific CDS service by ID")
    public ResponseEntity<CdsServiceConfigResponse> getService(@PathVariable String id) {
        log.info("Getting CDS service: {}", id);
        try {
            return ResponseEntity.ok(cdsHooksService.getService(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @Operation(summary = "Create a CDS service", description = "Creates a new CDS service configuration")
    public ResponseEntity<?> createService(@Valid @RequestBody CdsServiceConfigRequest request) {
        log.info("Creating CDS service: {}", request.getId());
        try {
            CdsServiceConfigResponse response = cdsHooksService.createService(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a CDS service", description = "Updates an existing CDS service configuration")
    public ResponseEntity<?> updateService(
            @PathVariable String id,
            @Valid @RequestBody CdsServiceConfigRequest request) {
        log.info("Updating CDS service: {}", id);
        try {
            CdsServiceConfigResponse response = cdsHooksService.updateService(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a CDS service", description = "Deletes a CDS service configuration")
    public ResponseEntity<?> deleteService(@PathVariable String id) {
        log.info("Deleting CDS service: {}", id);
        try {
            cdsHooksService.deleteService(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/enable")
    @Operation(summary = "Enable a CDS service", description = "Enables a CDS service")
    public ResponseEntity<?> enableService(@PathVariable String id) {
        log.info("Enabling CDS service: {}", id);
        try {
            CdsServiceConfigResponse response = cdsHooksService.toggleServiceEnabled(id, true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "Disable a CDS service", description = "Disables a CDS service")
    public ResponseEntity<?> disableService(@PathVariable String id) {
        log.info("Disabling CDS service: {}", id);
        try {
            CdsServiceConfigResponse response = cdsHooksService.toggleServiceEnabled(id, false);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
