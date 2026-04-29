package com.cqlplatform.controller;

import com.cqlplatform.entity.CdsFeedbackEntity;
import com.cqlplatform.model.cds.CdsServiceAnalyticsDTO;
import com.cqlplatform.model.cds.CdsServiceConfigRequest;
import com.cqlplatform.model.cds.CdsServiceConfigResponse;
import com.cqlplatform.service.cds.CdsAnalyticsService;
import com.cqlplatform.service.cds.CdsHooksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CdsAnalyticsService analyticsService;

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }

    @GetMapping
    @Operation(summary = "List CDS services", description = "Returns user's own + shared services")
    public ResponseEntity<List<CdsServiceConfigResponse>> getAllServices() {
        String username = getCurrentUsername();
        log.info("Listing CDS services for user: {}", username);
        if (isAdmin()) {
            return ResponseEntity.ok(cdsHooksService.getAllServices());
        }
        return ResponseEntity.ok(cdsHooksService.getServicesForUser(username));
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
    @Operation(summary = "Create a CDS service", description = "Creates a new CDS service configuration owned by current user")
    public ResponseEntity<?> createService(@Valid @RequestBody CdsServiceConfigRequest request) {
        String username = getCurrentUsername();
        log.info("Creating CDS service: {} for user: {}", request.getId(), username);
        try {
            CdsServiceConfigResponse response = cdsHooksService.createService(request, username);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a CDS service", description = "Updates an existing CDS service (must own or be admin)")
    public ResponseEntity<?> updateService(
            @PathVariable String id,
            @Valid @RequestBody CdsServiceConfigRequest request) {
        String username = getCurrentUsername();
        log.info("Updating CDS service: {} by user: {}", id, username);
        try {
            // Service-layer ownership enforcement (PAT-138). AccessDeniedException
            // propagates to GlobalExceptionHandler → 403; IllegalArgumentException → 400.
            CdsServiceConfigResponse response =
                    cdsHooksService.updateServiceIfOwnedBy(id, request, username, isAdmin());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a CDS service", description = "Deletes a CDS service (must own or be admin)")
    public ResponseEntity<?> deleteService(@PathVariable String id) {
        String username = getCurrentUsername();
        log.info("Deleting CDS service: {} by user: {}", id, username);
        try {
            cdsHooksService.deleteServiceIfOwnedBy(id, username, isAdmin());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/enable")
    @Operation(summary = "Enable a CDS service", description = "Enables a CDS service (must own or be admin)")
    public ResponseEntity<?> enableService(@PathVariable String id) {
        String username = getCurrentUsername();
        log.info("Enabling CDS service: {} by user: {}", id, username);
        try {
            CdsServiceConfigResponse response =
                    cdsHooksService.toggleServiceEnabledIfOwnedBy(id, true, username, isAdmin());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "Disable a CDS service", description = "Disables a CDS service (must own or be admin)")
    public ResponseEntity<?> disableService(@PathVariable String id) {
        String username = getCurrentUsername();
        log.info("Disabling CDS service: {} by user: {}", id, username);
        try {
            CdsServiceConfigResponse response =
                    cdsHooksService.toggleServiceEnabledIfOwnedBy(id, false, username, isAdmin());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/share")
    @Operation(summary = "Toggle service sharing", description = "Toggle shared status (admin only)")
    public ResponseEntity<?> toggleShared(@PathVariable String id, @RequestParam boolean shared) {
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only admins can share/unshare services"));
        }
        log.info("Setting service {} shared={}", id, shared);
        try {
            CdsServiceConfigResponse response = cdsHooksService.toggleShared(id, shared);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Versioning endpoints ---

    @GetMapping("/{serviceName}/versions")
    @Operation(summary = "Get service versions", description = "Returns all versions of a CDS service")
    public ResponseEntity<?> getServiceVersions(@PathVariable String serviceName) {
        log.info("Getting versions for service: {}", serviceName);
        try {
            List<CdsServiceConfigResponse> versions = cdsHooksService.getServiceVersions(serviceName);
            return ResponseEntity.ok(versions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{serviceName}/rollback/{version}")
    @Operation(summary = "Rollback service", description = "Rolls back a CDS service to a specific version (must own or be admin)")
    public ResponseEntity<?> rollbackService(
            @PathVariable String serviceName,
            @PathVariable int version) {
        String username = getCurrentUsername();
        log.info("Rolling back service {} to version {} by user: {}", serviceName, version, username);
        try {
            CdsServiceConfigResponse existing = cdsHooksService.getService(serviceName);
            if (!isAdmin() && existing.getOwnerUsername() != null && !existing.getOwnerUsername().equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only rollback your own services"));
            }
            CdsServiceConfigResponse response = cdsHooksService.rollbackService(serviceName, version);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Analytics endpoints ---

    @GetMapping("/analytics")
    @Operation(summary = "Get all service analytics", description = "Returns analytics for all CDS services")
    public ResponseEntity<?> getAllAnalytics() {
        if (analyticsService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(analyticsService.getAllServiceAnalytics());
    }

    @GetMapping("/{id}/analytics")
    @Operation(summary = "Get service analytics", description = "Returns analytics for a specific CDS service")
    public ResponseEntity<?> getServiceAnalytics(@PathVariable String id) {
        if (analyticsService == null) {
            return ResponseEntity.ok(CdsServiceAnalyticsDTO.builder().serviceId(id).build());
        }
        return ResponseEntity.ok(analyticsService.getServiceAnalytics(id));
    }

    // --- Feedback endpoint ---

    @GetMapping("/{id}/feedback")
    @Operation(summary = "Get service feedback", description = "Returns feedback for a specific CDS service")
    public ResponseEntity<List<CdsFeedbackEntity>> getServiceFeedback(@PathVariable String id) {
        return ResponseEntity.ok(cdsHooksService.getFeedback(id));
    }
}
