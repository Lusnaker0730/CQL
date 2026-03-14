package com.cqlplatform.controller;

import com.cqlplatform.model.cds.SandboxPresetRequest;
import com.cqlplatform.model.cds.SandboxPresetResponse;
import com.cqlplatform.service.cds.SandboxPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sandbox/presets")
@RequiredArgsConstructor
@Tag(name = "Sandbox Presets", description = "Sandbox patient data preset management")
public class SandboxPresetController {

    private final SandboxPresetService presetService;

    @GetMapping
    @Operation(summary = "List presets", description = "List own and shared sandbox presets")
    public ResponseEntity<List<SandboxPresetResponse>> listPresets(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(presetService.listAccessible(username));
    }

    @PostMapping
    @Operation(summary = "Create preset", description = "Create a new sandbox preset")
    public ResponseEntity<SandboxPresetResponse> createPreset(
            @Valid @RequestBody SandboxPresetRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        SandboxPresetResponse response = presetService.create(username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update preset", description = "Update an existing sandbox preset (owner only)")
    public ResponseEntity<SandboxPresetResponse> updatePreset(
            @PathVariable Long id,
            @Valid @RequestBody SandboxPresetRequest request) {
        return ResponseEntity.ok(presetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete preset", description = "Delete a sandbox preset (owner only)")
    public ResponseEntity<Void> deletePreset(@PathVariable Long id) {
        presetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
