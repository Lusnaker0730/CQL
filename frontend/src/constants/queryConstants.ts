/**
 * Centralized React Query cache timing constants.
 * Keeps staleTime / refetchInterval values consistent across all hooks and pages.
 */

/** Stable data that rarely changes (5 min) — libraries, departments, user lists */
export const STALE_5M = 5 * 60 * 1000

/** Moderate-frequency data (1 min) — eCQM artifacts, dashboard aggregates */
export const STALE_1M = 60_000

/** Frequently-changing data (30 s) — measures, notifications, live dashboards */
export const STALE_30S = 30_000

/** Immutable reference data — FHIR metadata, templates, modifiers */
export const STALE_FOREVER = Infinity

/** Polling interval for live dashboards and service status (30 s) */
export const REFETCH_30S = 30_000
