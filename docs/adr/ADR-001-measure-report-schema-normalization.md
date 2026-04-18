# ADR-001: Measure Report schema — blob → normalized (planned)

- **Status**: Proposed (follow-up to PAT-075 partial safeguards)
- **Date**: 2026-04-18
- **Related code review issue**: #6 (3 TEXT blobs — `measure_report.result_json`, `measure_definition.group_definitions`, `ecqm_artifact.population_groups`)

## Context

Three large entity tables currently store structured data as TEXT JSON blobs:

| Table | Column | Content | Hot-path readers |
|-------|--------|---------|------------------|
| `measure_report` | `result_json` | Full `MeasureEvaluationResult` — groups, populations, stratifiers, observation statistics | Dashboard, export (PDF/Excel/HQMF/QRDA), composite measures, comparison service |
| `measure_definition` | `group_definitions` | Authoring tree describing population criteria | CQL generator, authoring UI save/load |
| `ecqm_artifact` | `population_groups` | eCQM authoring tree | eCQM builder UI, CQL generator |

### Known problems

1. **Silent deserialization failures on schema drift.** A change to `MeasureEvaluationResult` (add required field, rename, etc.) without a data migration causes historical records to become unloadable. Until PAT-075 this was completely silent — `evaluationResult` fell back to null and the dashboard rendered empty groups. Fixed in PAT-075 with WARN log + counter, but the underlying fragility remains.
2. **Can't query population-level data from SQL.** Queries like "reports where numerator/denominator < 0.6 in department X" require app-level JSON parse of every row. Today dashboards dodge this by denormalizing `measure_score` / `total_patients` onto top-level columns, but drill-down queries need JSON.
3. **No DB-level referential integrity inside the blob.** A population whose `populationId` references a definition that was later renamed has no way to be found / updated by SQL.
4. **Large row sizes.** Some reports have 10+ groups × 6 populations × stratifier rows. Not a current bottleneck but scales poorly.

## Decision (current PR — PAT-075)

**Not the full normalization yet.** This PR is the defensive-layer shipping of code-review #6:

- DB-level CHECK constraints on `measure_report` (period order, score range, patient count non-negative).
- JPA `@PostLoad` deserialization failures now WARN-log with the report id and increment a process-wide counter (previously silent → null).
- This ADR documents the target state + migration plan.

Rationale for phasing: full normalization touches 7+ services (export, comparison, composite, reporting) and requires both schema migration and consumer refactor. Shipping the defensive layer in one PR gives us visibility and guardrails immediately, while the larger refactor can be sequenced separately.

## Target state (separate future PRs)

### Phase 2a: normalized `measure_report_group` + `measure_report_population`

```sql
CREATE TABLE measure_report_group (
    id BIGINT PRIMARY KEY,
    measure_report_id BIGINT NOT NULL REFERENCES measure_report(id) ON DELETE CASCADE,
    group_id VARCHAR(100) NOT NULL,
    description TEXT,
    measure_score DOUBLE PRECISION,
    measure_score_unit VARCHAR(100)
);

CREATE TABLE measure_report_population (
    id BIGINT PRIMARY KEY,
    measure_report_group_id BIGINT NOT NULL REFERENCES measure_report_group(id) ON DELETE CASCADE,
    population_type VARCHAR(50) NOT NULL,  -- 'initial-population', 'denominator', etc.
    population_id VARCHAR(100) NOT NULL,
    count INTEGER NOT NULL,
    subject_ids TEXT  -- JSON array, low-cardinality, rarely queried
);

CREATE INDEX idx_mrp_group_type ON measure_report_population(measure_report_group_id, population_type);
CREATE INDEX idx_mrg_report ON measure_report_group(measure_report_id);
```

**Migration**: dual-write phase — on save, write both `result_json` AND the normalized tables. Backfill script converts existing JSON to rows. After one release cycle with no regressions, `result_json` becomes read-fallback-only, then eventually dropped.

**Consumer migration order**:
1. DashboardService (lowest risk — read-only aggregation)
2. ThresholdAlert computation
3. Comparison service
4. Export services (hardest — need the full object graph, may keep reading `result_json` indefinitely)
5. Composite measure aggregation

### Phase 2b: normalized `ecqm_artifact_population_group`

Authoring tree is more complex (nested conjunction groups with modifier chains). Longer discussion.

### Phase 2c: `measure_definition.group_definitions` — keep as JSON

Authoring-tree semantics change frequently; the JSON-as-document model is actually appropriate here. Focus on: (a) schema version field, (b) `@PostLoad` surface failures (already done for reports), (c) migration-aware deserializer.

## Consequences

- **Shorter term (this PR)**: we get immediate DB-level protection + observability with zero consumer refactor. Dashboard hot path unchanged.
- **Medium term**: Phase 2a unlocks SQL-queryable dashboards ("show reports below threshold in department X by quarter") without app-level JSON parsing.
- **Long term**: existing `result_json` can be dropped once all consumers migrate, saving bytes and eliminating schema-drift risk.

## Alternatives considered

- **Full normalization in one big-bang PR.** Rejected — 7+ services to change, high regression risk, slow to review.
- **Move to PostgreSQL JSONB + schema validation.** Attractive (queryable via `->>`, `@>`, etc.) but still keeps the "document model" fragility for schema evolution and doesn't solve the no-FK-to-definition problem.
- **Do nothing.** Rejected — PAT-075 gives short-term safety but doesn't eliminate the growing liability.
