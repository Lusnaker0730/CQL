# Load Test Harness (k6)

How big a clinic can one `$evaluate-measure` handle on the production limits, and does the platform
stay responsive while evaluations run? This harness answers that with numbers instead of guesses:
it seeds a synthetic cohort of up to 10 000 patients into a real Docker stack and drives it with
[k6](https://k6.io) (PAT-266).

It complements the smoke harness (`scripts/smoke`, 47 scenarios, "is the logic right?") — this one asks
"is it still right, and how fast, at the size of a clinic?".

## What it measures

| Profile | Load | Pass criteria |
|---------|------|---------------|
| `scale` | one user runs `$evaluate-measure` (summary report) over the whole seeded cohort, twice, at every checkpoint: 500 → 1 000 → 2 000 → 5 000 → 10 000 patients (up to `LOAD_PATIENTS`) | HTTP 200, no failed patients, populations and score **equal the oracle** (the TypeScript judgement of the same generated patients, PAT-255), no evaluation timeout |
| `concurrency` | at the largest checkpoint, for `LOAD_DURATION` (3 min): 3 users evaluating the measure concurrently + 5 users executing single-patient CQL (the editor's hot path) + 10 users browsing the measure list | every evaluation still equals the oracle, HTTP failure rate < 1 %, browse p95 < 1 s, CQL execute p95 < 5 s |

The cohort is the platform's own TW Core patient generator with a fixed seed (`frontend/src/utils/loadCohort.ts`,
40 % diabetes-template patients), cut into transaction bundles of 100 patients; `expected.json` carries
the demo measure `DiabetesHbA1cRate`'s expected populations / score for every checkpoint prefix.
So an answer that comes back fast but wrong — a bulk fetch that silently drops resources, a timeout
that returns a partial denominator — fails the run.

## Running it

```bash
scripts/load/run.sh                              # 500 + 1 000 patients, then the concurrency mix at 1 000 (~10–15 min)
LOAD_PATIENTS=10000 scripts/load/run.sh          # the whole ladder; seeding ~100 000 resources into HAPI is most of the time
LOAD_PROFILE=scale scripts/load/run.sh           # only the ladder (LOAD_PROFILE=concurrency for only the mix)
scripts/load/run.sh --keep                       # leave the stack up (ports 19080 backend / 19081 HAPI / 19432 PG)
SMOKE_SKIP_BUILD=1 BACKEND_IMAGE_TAG=smoke-local scripts/load/run.sh   # a prebuilt backend image (same knob as smoke)
```

Requires Docker Desktop / docker compose v2, `jq`, `curl`, bash — and `node` + `npm` the first time, to
generate the cohort fixture (`scripts/load/data/cohort-10000/`, ~90 MB, gitignored; or run
`npm run gen:load-cohort -- 10000` in `frontend/` yourself). k6 itself runs from the `grafana/k6`
Docker image on the stack's network; nothing to install.

On GitHub the same run is the **Load Test** workflow (`.github/workflows/load.yml`, `workflow_dispatch`
only — it is not a PR gate): pick the largest checkpoint and the profile, read `summary.md` in the job
summary, download the k6 JSON and container logs from the artifact.

### Knobs

| Variable | Default | Meaning |
|----------|---------|---------|
| `LOAD_PATIENTS` | `1000` | largest checkpoint evaluated (500 / 1000 / 2000 / 5000 / 10000) |
| `LOAD_PROFILE` | `scale,concurrency` | which profiles run |
| `LOAD_MEASURE_TIMEOUT_SECONDS` | `120` (production) | `cql.execution.measure-timeout-seconds` for the stack — raise it to learn how long a big cohort really takes |
| `LOAD_BACKEND_CPUS` / `LOAD_BACKEND_MEMORY` | `2.0` / `1.5G` (production) | backend container limits |
| `LOAD_HAPI_CPUS` / `LOAD_HAPI_MEMORY` | `1.0` / `2G` (production) | HAPI container limits |
| `LOAD_EVAL_VUS` / `LOAD_EXECUTE_VUS` / `LOAD_BROWSE_VUS` / `LOAD_DURATION` | `3` / `5` / `10` / `3m` | the concurrency mix |
| `LOAD_ITERATIONS` | `2` | evaluations per checkpoint in the scale profile |
| `LOAD_SEED_PARALLEL` | `4` | parallel HAPI transactions while seeding |
| `LOAD_COHORT_SIZE` | `10000` | which fixture to use (`scripts/load/data/cohort-<size>/`) |
| `LOAD_RESULTS_DIR` | `scripts/load/results/<timestamp>/` | where summaries and logs go |
| `LOAD_K6_ENV_FILE` | — | a `docker --env-file` for k6 (`LOAD_USER` / `LOAD_PASSWORD` if the seeded admin is not `admin` / `admin`) |

The rate limiters are lifted by the smoke overlay this harness reuses (`scripts/smoke/compose.override.yml`),
so the numbers describe the engine and the FHIR path, not the per-IP throttle a single clinic IP would hit.

## Reading the results

`summary.md` (also printed at the end) has one row per checkpoint — evaluation time, oracle match, checks —
and one row for the concurrency mix, plus three backend log counters:

- `Measure evaluation timed out` — the cohort did not finish inside `measure-timeout-seconds`. The
  evaluation then **returns the patients that did finish** (status not `error`, `errorCount` > 0), which is a
  partial report; the `no failed patients` check catches it.
- `Bulk fetch failed` — the one-request-per-resource-type bulk fetch failed for the cohort (the request
  carries every patient id).
- ERROR log lines.

The k6 JSON (`scale-<n>.json`, `concurrency-<n>.json`) has every metric; `evaluate_measure_ms` is the
end-to-end `$evaluate-measure` latency, `http_req_duration{scenario:browse}` the browsing latency under load.

## Last measured (2026-10-15, laptop host, production limits)

Backend 2 CPU / 1.5 GB, HAPI 1 CPU / 2 GB, measure timeout 120 s, backend image built from this PR; two
evaluations per checkpoint, all equal to the oracle, 0 timeouts, 0 bulk fetch failures.

| patients | resources (cohort) | evaluate avg | max | oracle |
|---|---|---|---|---|
| 500 | 5 131 | 15.7 s | 21.1 s | match |
| 1 000 | 10 196 | 16.2 s | 18.9 s | match |
| 2 000 | 20 491 | 21.9 s | 22.1 s | match |
| 5 000 | 51 255 | 43.6 s | 48.9 s | match |
| 10 000 | 102 501 | 178 s | 183 s | match (bulk fetch 134 s in 500 requests; CQL phase ≈ 35 s) |

Concurrency at 1 000 patients (3 evaluations + 5 CQL executions + 10 browsing users for 3 min): before the
BUG-152 fix every evaluation took ~15 min and came back with `errorCount` and a wrong score; after it,
evaluations average 44 s (30–56 s) and all equal the oracle, CQL execute p95 4.9 s (threshold 5 s — tight),
browse p95 0.2 s, 0 of 4 493 requests failed.

The very first run failed at 500 patients: the bulk fetch carried every patient id in one request line and
HAPI answered 400 (BUG-151, fixed in the same PR). At 10 000 patients the fetch dominates (500 sequential
requests on HAPI's single CPU); parallel batches are the next optimisation if that ever matters. The HAPI
image in use honoured `_count=5000` for 3 236 resources without clamping; the paging loop is a safeguard.
Re-run before quoting a number — the engine, HAPI and the cohort all move; the production VM has 2 cores
in total and was not measured.
