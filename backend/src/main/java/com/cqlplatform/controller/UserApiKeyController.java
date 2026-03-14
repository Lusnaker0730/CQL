package com.cqlplatform.controller;

import com.cqlplatform.entity.UserApiKeyEntity;
import com.cqlplatform.model.request.ApiKeyCreateRequest;
import com.cqlplatform.service.UserApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/api-keys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User API Keys", description = "APIs for managing per-user API keys for CDS Hooks")
public class UserApiKeyController {

    private final UserApiKeyService apiKeyService;

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @GetMapping
    @Operation(summary = "List API keys", description = "Returns all API keys for the current user")
    public ResponseEntity<List<Map<String, Object>>> listKeys() {
        String username = getCurrentUsername();
        List<UserApiKeyEntity> keys = apiKeyService.listKeys(username);

        // Don't expose the full API key or hash in list responses
        List<Map<String, Object>> response = keys.stream()
                .map(k -> Map.<String, Object>of(
                        "id", k.getId(),
                        "name", k.getName() != null ? k.getName() : "",
                        "keyPreview", k.getKeyPrefix() != null ? k.getKeyPrefix() + "..." : "****",
                        "createdAt", k.getCreatedAt() != null ? k.getCreatedAt().toString() : "",
                        "lastUsedAt", k.getLastUsedAt() != null ? k.getLastUsedAt().toString() : "",
                        "active", k.getActive()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Generate API key", description = "Generates a new API key for the current user")
    public ResponseEntity<Map<String, Object>> generateKey(@Valid @RequestBody ApiKeyCreateRequest body) {
        String username = getCurrentUsername();
        String name = body.getName() != null ? body.getName() : "Unnamed Key";

        UserApiKeyEntity key = apiKeyService.generateApiKey(username, name);

        // Return the full key only on creation
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", key.getId(),
                "name", key.getName(),
                "key", key.getApiKey(),
                "createdAt", key.getCreatedAt().toString(),
                "active", key.getActive()
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API key", description = "Revokes an API key for the current user")
    public ResponseEntity<?> revokeKey(@PathVariable Long id) {
        String username = getCurrentUsername();
        boolean revoked = apiKeyService.revokeKey(id, username);

        if (revoked) {
            return ResponseEntity.ok(Map.of("message", "API key revoked"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "API key not found or not owned by you"));
        }
    }

}
