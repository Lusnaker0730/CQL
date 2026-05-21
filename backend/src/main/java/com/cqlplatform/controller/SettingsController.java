package com.cqlplatform.controller;

import com.cqlplatform.config.AiProperties;
import com.cqlplatform.service.ai.CqlFixService;
import com.cqlplatform.service.fhir.VsacService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Class-level {@code @PreAuthorize} (PAT-145) — every settings endpoint requires
 * ADMIN. Previously only PUT was rolemark via {@code SecurityConfig:192}, leaving
 * GETs ({@code /vsac-status}, {@code /ai-status}) reachable by any authenticated
 * user — a non-admin could read the configured VSAC URL and AI provider/model.
 * The PUT-only rule made the original intent ambiguous; locking the whole class
 * to ADMIN closes the gap and matches the operator-only nature of these endpoints.
 */
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final VsacService vsacService;
    private final AiProperties aiProperties;
    private final CqlFixService cqlFixService;

    /**
     * PAT-145 — replaces field-level {@code @Autowired(required=false)} with
     * constructor injection using {@link Optional}, matching the convention from
     * PAT-141 ({@code CqlTranslationService}) and PAT-143 ({@code JwtAuthenticationFilter}).
     */
    public SettingsController(VsacService vsacService,
                               AiProperties aiProperties,
                               Optional<CqlFixService> cqlFixService) {
        this.vsacService = vsacService;
        this.aiProperties = aiProperties;
        this.cqlFixService = cqlFixService.orElse(null);
    }

    @GetMapping("/vsac-status")
    public ResponseEntity<Map<String, Object>> getVsacStatus() {
        return ResponseEntity.ok(Map.of(
                "configured", vsacService.isConfigured(),
                "url", vsacService.getApiUrl()
        ));
    }

    @PutMapping("/vsac-api-key")
    public ResponseEntity<Map<String, Object>> updateVsacApiKey(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        vsacService.updateApiKey(apiKey);
        return ResponseEntity.ok(Map.of(
                "configured", vsacService.isConfigured(),
                "message", "VSAC API key updated"
        ));
    }

    @GetMapping("/ai-status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        boolean enabled = cqlFixService != null;
        String provider = enabled ? cqlFixService.getProviderName() : "none";
        String model = enabled ? cqlFixService.getModelName() : null;

        boolean configured = enabled;
        if ("cloud".equals(provider)) {
            String key = aiProperties.getCloudApiKey();
            configured = key != null && !key.isBlank();
        }

        if (model != null) {
            return ResponseEntity.ok(Map.of(
                    "enabled", enabled,
                    "provider", provider,
                    "model", model,
                    "configured", configured
            ));
        }
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "provider", provider,
                "configured", configured
        ));
    }

    @PutMapping("/ai-api-key")
    public ResponseEntity<Map<String, Object>> updateAiApiKey(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        if (apiKey != null && !apiKey.isBlank()) {
            aiProperties.setCloudApiKey(apiKey);
            return ResponseEntity.ok(Map.of(
                    "configured", true,
                    "message", "AI API key updated"
            ));
        }
        return ResponseEntity.ok(Map.of(
                "configured", false,
                "message", "AI API key cleared"
        ));
    }
}
