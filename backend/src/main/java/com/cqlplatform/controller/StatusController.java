package com.cqlplatform.controller;

import com.cqlplatform.model.PlatformStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

/**
 * PAT-209 — public status endpoint powering the /status page. Anonymous (permitAll in
 * SecurityConfig). Reports coarse reachability only: that the API responds and the database
 * is reachable. No internal details, counts, component versions, or PHI are exposed.
 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
@Slf4j
public class StatusController {

    private final DataSource dataSource;

    @GetMapping
    public ResponseEntity<PlatformStatus> status() {
        boolean dbOk = isDatabaseReachable();
        // The API is responding by definition (we are inside a handler). Overall is operational
        // only when every checked component is up.
        List<PlatformStatus.Component> components = List.of(
                new PlatformStatus.Component("api", true),
                new PlatformStatus.Component("database", dbOk));
        String overall = components.stream().allMatch(PlatformStatus.Component::ok)
                ? "operational" : "degraded";
        return ResponseEntity.ok(new PlatformStatus(overall, Instant.now().toString(), components));
    }

    private boolean isDatabaseReachable() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            log.warn("Status check: database not reachable: {}", e.getMessage());
            return false;
        }
    }
}
