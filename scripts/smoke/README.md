# Smoke Test Harness

Local end-to-end integration smoke test for the CQL Platform. Each scenario
exercises a complete scoring-type pipeline (save → publish → evaluate) against a
real Docker stack, catching integration regressions that unit tests miss.

## Why this exists

Unit tests pass ≠ the app works. We've shipped several PRs that were green at
1000+ unit tests but broken end-to-end (BUG-110 `ToInterval(null)` dispatch,
BUG-111 cross-library retrieve, #230 wire-shape change). The feedback loop had
been "merge → deploy to VM → user reports → investigate" — a ~15 min cycle
where the user was the smoke test.

This harness runs the same flow the user does — POST a measure, seed FHIR data,
evaluate — in ~60–120 seconds on your laptop, before `git push`.

## Coverage

One scenario per eCQM scoring type (see scenarios/). Each type has a completely
different CQL shape and population structure, so bugs rarely cross over — this
is why we need all four, not just one:

| Scenario | Scoring | What it exercises |
|----------|---------|-------------------|
| `01-proportion-age-cohort` | `proportion` | IP / Denom / Numer; `measureScore = numer/denom` as percentage. Locked at IP=7 / Denom=5 / Numer=3 / score=60.0. |
| `02-ratio-age-comparison` | `ratio` | Ratio with independent Numer / Denom (fixed in #PAT-084). Disjoint cohorts: Denom=young-adults (2 patients), Numer=seniors (3 patients). score=150.0 (3/2 > 100%) — proves ratio evaluator treats Numer independently of Denom. |
| `03-cv-count-adults` | `continuous-variable` (patient-based, Count) | Patient-based CV with boolean Measure Observation. Exercises `populations.measure-population` key + `observations[]` block with `aggregateMethod=Count`. Boolean → 1.0 extraction (fixed in #PAT-085). IP=3 / MP=3 / score=3.0. |
| `04-cohort-adult-count` | `cohort` | IP-only; cohort score = IP count (fixed in #PAT-083). IP=4 / score=4.0 / measureScoreUnit=\"count\". |
| `05-cv-avg-encounter-duration` | `continuous-variable` (episode-based, Average) | Episode-based CV on Encounter. `populationBasis=Encounter`, Measure Observation = `duration in days of Encounter.period`. 5 encounters with durations {2,4,6,8,10} days → Average = 6.0. Exercises the episode-based `Measure Observation Values` emit: `(\"Measure Population\") MP return \"Measure Observation\"(MP)`. |
| `06-cv-sum-encounter-duration` | `continuous-variable` (episode-based, Sum) | Same fixture as 05, aggregateMethod=Sum. Expected 2+4+6+8+10 = 30.0. |
| `07-cv-median-encounter-duration` | `continuous-variable` (episode-based, Median) | Same fixture, aggregateMethod=Median. Middle value of sorted list = 6.0. |
| `08-cv-min-encounter-duration` | `continuous-variable` (episode-based, Minimum) | Same fixture, aggregateMethod=\"Min\" (alias — #PAT-088). min{...} = 2.0. |
| `09-cv-max-encounter-duration` | `continuous-variable` (episode-based, Maximum) | Same fixture, aggregateMethod=\"Max\" (alias). max{...} = 10.0. |
| `10-cds-patient-view-basic` | CDS Hook (patient-view) | Minimal hardcoded Tuple card → info indicator. Proves save → discover → invoke → CQL → card pipeline. |
| `11-cds-patient-view-conditional` | CDS Hook (patient-view) | `exists([Condition])` on prefetch → warning card when patient has a condition. Proves prefetch-driven clinical logic. |
| `12-cds-order-sign` | CDS Hook (order-sign) | Non-patient-view hook + `draftOrders` context. Tests hook dispatch beyond the default patient-view. (Uses `order-sign`, the modern CDS Hooks replacement for deprecated `medication-prescribe`.) |
| `13-cds-multi-card-indicators` | CDS Hook (patient-view) | 3 independent Tuple defines → 3 cards (info/warning/critical). Per-card field assertions omitted because `CqlTupleCardStrategy` Map-iteration order isn't guaranteed. |
| `14-cds-disabled-service-not-listed` | CDS Hook | Service saved with `enabled: false`. Backend returns **HTTP 200 + info card** `\"Service not found\"` (not 404). Discovery omits the service (only `enabled=true` services populate `serviceConfigs`). |
| `15-cds-dryrun-mode` | CDS Hook (patient-view) | Invoked with `dryRun: true` + `debugMode: true`. 0 cards (CQL skipped) but `debug.prefetchStatus` populated. Proves dryRun short-circuit and prefetch resolution are independent of CQL run. |
| `16-cql-execute-debug-trace` | CQL execute (`/api/cql/execute`) | POST with `debugMode: true`. Asserts `debugTrace.expressionTraces[]` is populated (min 3 entries for 3 defines), each entry carries `name`/`resultType`/`evaluationTimeMs`/`order`, `debugTrace.elmJson` non-empty, `totalTimeMs` is a number. Field-presence assertions only — trace schema is diagnostic UX and shouldn't be over-locked. |
| `18-cds-error-debug-trace` | CDS Hook (patient-view, error path) | Service has CQL referencing an undefined function. Invoked with `debugMode: true`. Asserts response is **HTTP 200** (not 5xx) with `debug.error.phase = \"cql_translation\"` + structured `errorType` / `message`. Protects the contract that EHR integrators get structured error info instead of bare stack traces. |

**CDS scenario files**: `service.json` (CdsServiceConfigRequest), `invocation.json` (CdsRequest — hook + context + prefetch), `expected.json` with `type: \"cds-hook\"` plus `cardCount` / `cards[]` / `expectNoCards` / `debugPrefetchNonEmpty` / `debugErrorPhase` / `debugErrorRequiredFields` assertions. `run.sh` dispatches by the `type` field (default `ecqm`).

**CQL-execute scenario files**: `request.json` (CqlExecutionRequest — body posted verbatim to `/api/cql/execute`), `expected.json` with `type: \"cql-execute\"` plus `success` / `expressionTracesMinCount` / `expressionTraceRequiredFields` / `retrieveTracesMinCount` / `elmJsonNonEmpty` / `totalTimeMsPresent` assertions. No FHIR seeding; pure debug-trace contract test.

> **aggregateMethod naming (since #PAT-088)**: Canonical forms are `count` / `sum` / `average` / `median` / `minimum` / `maximum`. Case-insensitive aliases accepted: `Min`→`minimum`, `Max`→`maximum`, `Avg`/`Mean`→`average`. Unknown methods (typos like `\"Minumum\"`) return `null` score with a logged warning — they no longer silently fall through to Average.

**Out of scope**: element / modifier / value-set CQL generation. Those are
locked by `ModifierGeneratedCqlGoldenTest` (in-process, fast, 15 scenarios).
This harness only cares about the **scoring-type pipeline** through the real
stack — ports, auth, Flyway migrations, bean wiring, HAPI round-trip.

## Prerequisites

- Docker Desktop (or equivalent) — daemon must be running
- `jq`, `curl`, `bash` on PATH
- Free ports in the 18xxx range (configurable — see below)

## Usage

```bash
# All scenarios
scripts/smoke/run.sh

# One scenario (glob)
scripts/smoke/run.sh 01-proportion-*

# Debug: leave stack running after (tear down manually)
scripts/smoke/run.sh --keep

# Custom ports (default 18080/18081/18432)
SMOKE_BACKEND_PORT=28080 SMOKE_FHIR_PORT=28081 scripts/smoke/run.sh
```

Teardown is automatic on exit (success or failure). Use `--keep` when debugging
a scenario failure to inspect the live stack.

## Adding a scenario

Create `scenarios/<NN-name>/` with three files:

- **`measure.json`** — full `EcqmArtifactRequest` body. Cross-reference the
  shape with `backend/src/main/java/com/cqlplatform/model/ecqm/EcqmArtifactRequest.java`
  and the test helpers in `EcqmCqlBuilderTest` for valid tree structures.
- **`bundle.json`** — FHIR transaction Bundle. Entries should use
  `request.method: PUT` with `fullUrl: <Type>/<id>` to preserve client-side IDs.
- **`expected.json`** — assertion targets:
  ```json
  {
    "periodStart": "2020-01-01",
    "periodEnd":   "2020-06-30",
    "score": 60.0,          // percentage 0-100 (backend normalizes)
    "scoreTolerance": 0.5,  // optional, defaults 0.001
    "populations": {
      "initial-population": 7,
      "denominator": 5,
      "numerator": 3
    }
  }
  ```

### Isolation between scenarios

Scenarios share the stack — Docker is expensive to bring up. To avoid cross-
scenario pollution, **use disjoint Measurement Periods per scenario**. Each
scenario's patient data should only contain observations/encounters in its
period window. We suggest:

| Scenario | Period |
|----------|--------|
| `01-proportion-*` | 2020-H1 (2020-01-01 → 2020-06-30) |
| `02-ratio-*`      | 2020-H2 (2020-07-01 → 2020-12-31) |
| `03-cv-*`         | 2021-H1 (2021-01-01 → 2021-06-30) |
| `04-cohort-*`     | 2021-H2 (2021-07-01 → 2021-12-31) |

### Age-bracket stability

No special handling needed. `AgeRange` elements in eCQM artifacts emit
`AgeInYearsAt(end of "Measurement Period")` (since #PAT-081), so ages are
computed at the period-end reference point and are reproducible regardless of
when the scenario runs.

## Exit codes

- `0` — all scenarios passed
- `1` — one or more scenarios failed (details per scenario on stderr)

## Known limitations

- First run is slow (~2 min) because Docker has to build the backend image.
  Subsequent runs with a warm cache finish in 60–90s.
- No cross-scenario ordering enforcement — scenarios must be self-contained.
- Doesn't cover frontend regressions. That needs Playwright; out of scope for
  this harness.
