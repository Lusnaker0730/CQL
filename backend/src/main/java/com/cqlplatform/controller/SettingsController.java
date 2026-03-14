package com.cqlplatform.controller;

import com.cqlplatform.config.AiProperties;
import com.cqlplatform.service.ai.CqlFixService;
import com.cqlplatform.service.fhir.VsacService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final VsacService vsacService;
    private final AiProperties aiProperties;

    @Autowired(required = false)
    private CqlFixService cqlFixService;

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
