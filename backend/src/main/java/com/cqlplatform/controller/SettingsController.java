package com.cqlplatform.controller;

import com.cqlplatform.service.fhir.VsacService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final VsacService vsacService;

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
}
