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
| `01-proportion-*` | `proportion` | IP / Denom / Numer; `measureScore = numer/denom` |
| `02-ratio-*` (TODO) | `ratio` | Dual IP; exclusion populations |
| `03-cv-*` (TODO) | `continuous-variable` | MeasurePopulation list preservation (`RenderMode.CV_MEASURE_POPULATION` — the #239 refactor), MeasureObservation aggregate |
| `04-cohort-*` (TODO) | `cohort` | IP-only; `measureScore = count(IP)` |

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

### Age-bracket stability (applies to AgeRange scenarios)

`AgeRange` elements currently emit `AgeInYears()` — which in CQL uses the
system clock, **not** Measurement Period end. For a smoke test to produce
stable results over time, pick birth dates where the age-bracket categorization
(adult / senior / child / etc.) is stable for at least the next several years.
`01-proportion-age-cohort/bundle.json` has worked examples with an inline note.

(If/when `AgeRange` starts using `AgeInYearsAt(end of "Measurement Period")`,
this caveat goes away.)

## Exit codes

- `0` — all scenarios passed
- `1` — one or more scenarios failed (details per scenario on stderr)

## Known limitations

- First run is slow (~2 min) because Docker has to build the backend image.
  Subsequent runs with a warm cache finish in 60–90s.
- No cross-scenario ordering enforcement — scenarios must be self-contained.
- Doesn't cover frontend regressions. That needs Playwright; out of scope for
  this harness.
