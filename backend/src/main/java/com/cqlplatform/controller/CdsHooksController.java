package com.cqlplatform.controller;

import com.cqlplatform.model.cds.*;
import com.cqlplatform.service.cds.CdsHooksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.cqlplatform.util.IdGenerator;

@RestController
@RequestMapping("/cds-services")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CDS Hooks", description = "CDS Hooks Service APIs")
public class CdsHooksController {

    private final CdsHooksService cdsHooksService;

    // --- Global discovery (shared services only) ---

    @GetMapping({"", "/"})
    @Operation(summary = "Discovery", description = "Returns shared/global CDS services")
    public ResponseEntity<Map<String, List<CdsServiceDefinition>>> discovery() {
        List<CdsServiceDefinition> services = cdsHooksService.getSharedServiceDefinitions();
        return ResponseEntity.ok(Map.of("services", services));
    }

    @PostMapping("/{serviceId}")
    @Operation(summary = "Invoke CDS Service", description = "Invokes a specific CDS service")
    public ResponseEntity<?> invokeService(
            @PathVariable String serviceId,
            @Valid @RequestBody CdsRequest request) {
        log.info("CDS invoke: serviceId={}, hook={}, fhirServer={}, prefetchKeys={}, patientId={}",
                serviceId, request.getHook(), request.getFhirServer(),
                request.getPrefetch() != null ? request.getPrefetch().keySet() : "null",
                request.getContext() != null ? request.getContext().getPatientId() : "null");
        try {
            CdsResponse response = cdsHooksService.invokeService(serviceId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{serviceId}/feedback")
    @Operation(summary = "Submit Feedback", description = "Submit feedback for a CDS service invocation")
    public ResponseEntity<?> submitFeedback(
            @PathVariable String serviceId,
            @Valid @RequestBody CdsFeedbackRequest feedback) {
        try {
            cdsHooksService.processFeedback(serviceId, feedback);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{serviceId}/sandbox")
    @Operation(summary = "Sandbox Invoke", description = "Invoke a CDS service in sandbox mode with test data")
    public ResponseEntity<?> sandboxInvoke(
            @PathVariable String serviceId,
            @Valid @RequestBody CdsSandboxRequest sandboxRequest) {
        log.info("CDS sandbox invoke: serviceId={}", serviceId);
        try {
            CdsRequest cdsRequest = new CdsRequest();
            cdsRequest.setHook(sandboxRequest.getHook());
            cdsRequest.setHookInstance(sandboxRequest.getHookInstance() != null
                    ? sandboxRequest.getHookInstance() : IdGenerator.uuid());
            cdsRequest.setContext(sandboxRequest.getContext());
            // Use testData as prefetch, no fhirServer -> forces PrefetchRetrieveProvider
            cdsRequest.setPrefetch(sandboxRequest.getTestData());

            CdsResponse response = cdsHooksService.invokeService(serviceId, cdsRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Per-user CDS endpoints (API key authenticated) ---

    @GetMapping("/u/{username}")
    @Operation(summary = "Per-user Discovery", description = "Returns CDS services for a specific user (API key auth)")
    public ResponseEntity<Map<String, List<CdsServiceDefinition>>> perUserDiscovery(
            @PathVariable String username) {
        // Auth is handled by the JwtAuthenticationFilter (API key path)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getName().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        log.info("Per-user CDS discovery for: {}", username);
        List<CdsServiceDefinition> services = cdsHooksService.getServiceDefinitionsForUser(username);
        return ResponseEntity.ok(Map.of("services", services));
    }

    @PostMapping("/u/{username}/{serviceId}")
    @Operation(summary = "Per-user Hook Invocation", description = "Invokes a CDS service for a specific user (API key auth)")
    public ResponseEntity<?> perUserInvokeService(
            @PathVariable String username,
            @PathVariable String serviceId,
            @Valid @RequestBody CdsRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getName().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        log.info("Per-user CDS invoke: user={}, serviceId={}", username, serviceId);
        try {
            CdsResponse response = cdsHooksService.invokeService(serviceId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
