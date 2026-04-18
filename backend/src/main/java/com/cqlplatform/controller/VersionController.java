package com.cqlplatform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Deploy-change detection for the frontend.
 *
 * <p>Frontend polls {@code /api/version} and compares the response to the value it
 * received on initial load. A mismatch signals the backend was restarted (new deploy,
 * crash-recovery, manual restart) — the SPA then invalidates its React Query cache so
 * users see the new API schema without having to hard-refresh.
 *
 * <p>{@code startupTime} is the primary change signal: it changes on every backend
 * restart, which is a superset of "code changed". This is intentionally more
 * conservative than {@code commitSha} — a no-op restart still busts client cache,
 * avoiding the class of bug where the server restarts with a hot-patched jar but the
 * SHA looks identical.
 *
 * <p>{@code commitSha} is optional: when {@code APP_COMMIT_SHA} env var is set (at
 * deploy time), it's returned for human-readable deploy tracking. Frontend equality
 * check uses the whole response, so sha mismatch also triggers invalidation.
 *
 * <p>Unauthenticated endpoint — the frontend must be able to poll before login and
 * during session refresh races. Nothing sensitive is exposed: startup timestamp and
 * commit sha are information the deploy pipeline already makes public.
 */
@RestController
@RequestMapping("/api/version")
@Slf4j
@Tag(name = "Version", description = "Backend deploy-change detection for client cache invalidation")
public class VersionController {

    private static final Instant STARTUP_TIME = Instant.now();

    @Value("${app.commit-sha:unknown}")
    private String commitSha;

    @Value("${app.version:unknown}")
    private String appVersion;

    @GetMapping
    @Operation(summary = "Returns backend build/startup info for SPA cache-bust detection")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of(
                "startupTime", STARTUP_TIME.toString(),
                "commitSha", commitSha,
                "version", appVersion
        ));
    }
}
