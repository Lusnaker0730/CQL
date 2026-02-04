package com.cqlplatform.controller;

import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.model.cds.CdsServiceDefinition;
import com.cqlplatform.service.cds.CdsHooksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cds-services")
@RequiredArgsConstructor
@Tag(name = "CDS Hooks", description = "CDS Hooks Service APIs")
public class CdsHooksController {

    private final CdsHooksService cdsHooksService;

    @GetMapping
    @Operation(summary = "Discovery", description = "Returns available CDS services")
    public ResponseEntity<Map<String, List<CdsServiceDefinition>>> discovery() {
        List<CdsServiceDefinition> services = cdsHooksService.getServiceDefinitions();
        return ResponseEntity.ok(Map.of("services", services));
    }

    @PostMapping("/{serviceId}")
    @Operation(summary = "Invoke CDS Service", description = "Invokes a specific CDS service")
    public ResponseEntity<CdsResponse> invokeService(
            @PathVariable String serviceId,
            @RequestBody CdsRequest request) {
        CdsResponse response = cdsHooksService.invokeService(serviceId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{serviceId}/feedback")
    @Operation(summary = "Submit Feedback", description = "Submit feedback for a CDS service invocation")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable String serviceId,
            @RequestBody Map<String, Object> feedback) {
        return ResponseEntity.ok().build();
    }
}
