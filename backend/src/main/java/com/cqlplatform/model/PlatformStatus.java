package com.cqlplatform.model;

import java.util.List;

/**
 * PAT-209 — public platform status for the /status page. Deliberately coarse: it reports
 * overall reachability plus a couple of component checks (API responding, database reachable)
 * and never exposes internal details, counts, or PHI. Served anonymously.
 *
 * @param status    "operational" when every component is ok, otherwise "degraded"
 * @param timestamp ISO-8601 instant the status was computed
 * @param components per-component up/down
 */
public record PlatformStatus(String status, String timestamp, List<Component> components) {

    public record Component(String name, boolean ok) {}
}
