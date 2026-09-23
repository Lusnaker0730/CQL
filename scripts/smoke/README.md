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

## Boot check

Before any scenario runs, the harness requires that the backend came up on its **first** start: a container
restart count other than 0 fails the run and prints the `Caused by:` lines from the backend log. With
`restart: unless-stopped`, a backend that crashes during its first boot and recovers on the restart looks
perfectly healthy to a health-endpoint wait — BUG-144 (the demo measure was inserted without a tenant while
`measure_definition.tenant_id` is NOT NULL — by code reading, since V61 of 2026-07-09) crashed the first boot of a fresh database that way,
and no H2 test could see it because `DataInitializer` only runs under the `dev` / `docker` profiles. The check
also asserts that the fresh database holds exactly one seeded demo measure (`DiabetesHbA1cRate`).

## Report persistence check

After the last scenario, the harness fails the run if the backend log contains `Failed to save measure report`,
and prints the violated constraint. An evaluation whose report cannot be saved still answers 200 with the right
numbers (`autoSaveReport` deliberately does not let a save failure break the response), so every scenario passes
while the record is silently missing. BUG-145 was found that way only by reading a log: a wall-clock subtraction
produced a negative evaluation duration, and the CHECK on `measure_report.evaluation_duration_ms` rejected the row.

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
| `17-cds-retrieve-cache-dedupe` | CDS Hook (patient-view, BUG-116 regression) | Service with 3 defines + 1 Card expression all referencing `[Observation]`. Invoked with `debugMode: true`. Asserts `debug.debugTrace.retrieveTraces.length == 1` — proves the engine batch eval + provider-level memoization dedupe retrieves across expression references (pre-fix: 10 rows; post-fix: 1). |
| `18-cds-error-debug-trace` | CDS Hook (patient-view, error path) | Service has CQL referencing an undefined function. Invoked with `debugMode: true`. Asserts response is **HTTP 200** (not 5xx) with `debug.error.phase = \"cql_translation\"` + structured `errorType` / `message`. Protects the contract that EHR integrators get structured error info instead of bare stack traces. |
| `19-proportion-stratifier-sde` | `proportion` + Stratifier + SDE | Locks `StratifierEvaluator` end-to-end + standard SDE define generation. 7 patients with mixed gender. Asserts `groups[0].stratifiers[0].strataId == "gender"` with two strata (`true` for males / `false` for females), per-stratum populations + scores, and that `define "SDE Sex"` / `define "SDE Payer"` appear in the published CQL (fetched via `/api/measures/{id}/export/cql`). |
| `20-cql-execute-error-info` | CQL execute (error path) | POST broken CQL. Asserts HTTP **500** with `errorInfo.phase = \"cql_translation\"` + structured `errorType` / `message` on the top-level `ErrorResponse`. PAT-098 contract lock — integrators see structured phase classification on editor failures instead of a flat message string. |
| `21-proportion-with-exclusions` | `proportion` (6-pop) | Locks the full 6-population pipeline: IP, Denom, **DenomExcl** (age >=80), Numer, **NumerExcl** (gender = male). Catches three-valued logic regressions (PAT-130 family). 7 patients yield IP=7 / Denom=6 / DenomExcl=1 / Numer=2 / NumerExcl=1 / score=40.0 — `Numerator` count is the *post-exclusion* effective count per `PopulationEvaluator.aggregatePatientResults`. |
| `22-external-cql-library` | `proportion` + external lib | Locks the `DatabaseLibrarySourceProvider` integration path (BUG-107 family). Uploads `library.cql` via `POST /api/cql/libraries` before publish; measure tree contains `externalCqlRef` nodes pointing at `SmokeExternalUtil.IsAdultAtPeriodEnd` / `IsSeniorAtPeriodEnd`. Engine must resolve the include + execute external defines at evaluate time. |
| `23-export-csv-injection` | `proportion` + export | CSV injection regression lock. Measure name starts with `=`, description with `+`, group description with `-`, group ID with `@`. After evaluate, fetches the latest report and exports as CSV; asserts no row begins with a formula-trigger character (`=` / `+` / `-` / `@` / `\t` / `\r`) — ensures `CsvUtils.escapeCsv` neutralization survives the full export pipeline. |
| `24-empty-ip-null-score` | `proportion` (empty IP edge) | Locks the empty-cohort / zero-denominator path. 3 patients exist (so `PatientDiscoveryService` finds them and evaluation runs), but IP criteria `>=200` rejects all → IP=0. Asserts score is `null` per `MeasureScoreCalculator.calculateProportionScore` (zero denom → null). Catches regressions where empty cohort produces NaN, 0.0, or a 5xx — all of which would silently corrupt dashboard aggregations. |
| `25-multi-population-group` | `proportion` (2 groups) | BUG #474 regression lock. Two independent groups (`working-age` / `seniors`) report distinct populations + scores via the `groups[]` assertion knob. Pre-fix all populations were 0 because `MeasureEvaluationService.aggregatePerPatient` fed `PopulationEvaluator` raw results with suffixed CQL define names (`Initial Population 1` / `Initial Population 2`) but the evaluator hard-coded lookups for unsuffixed canonical names. Fix: per-group iteration in aggregation + `PopulationEvaluator.buildExpressionMap` canonicalization per group. |
| `26-concurrent-evaluation` | `proportion` (concurrency stress) | Fires 5 parallel `$evaluate-measure` calls and asserts all produce identical score+populations. Stress-tests connection pools (HikariCP, HAPI client, `cqlExecutionExecutor`) and concurrent `MeasureReportService.saveReport` inserts. Catches pool starvation, thread-leak, and concurrent-insert collisions that don't surface in single-call tests. |
| `27-multi-group-stratifier` | `proportion` (2 groups + per-group stratifier) | BUG-474 stratifier follow-up. Two groups (`working-age` / `seniors`), each with its own gender stratifier define (`Stratifier gender 1` / `Stratifier gender 2`). Pre-fix `StratifierEvaluator.evaluatePatientStratifiers` looked up populations against unsuffixed canonical names that didn't exist in multi-group CQL output, causing strata counts to be 0. Fix routes per-group canonicalization via `PopulationEvaluator.buildExpressionMap` so each stratifier resolves populations against its own group's results. Asserts per-group `stratifiers[]` via the `groups[].stratifiers[]` knob. |
| `29-multi-group-cv-counts` | `continuous-variable` (2 groups, Count) | Issue #539 regression lock. Two groups (`adults` / `seniors`), each with its own observation define emitted as `Measure Observation Value 1` / `Measure Observation Value 2`. Pre-fix `aggregateCvPatientResults` only looked up the unsuffixed canonical name → both groups' scores were null. Fix: 5-arg overload accepts per-group observation expression names, `MeasureEvaluationService.AggregationState` tracks `observationValuesByGroup` per group, multi-group dispatch routes CV to `buildMultiGroupResult` (which now learns to compute CV scores per group). Adults score=4.0, seniors score=2.0 — independent Count aggregations from disjoint population thresholds. |
| `36-value-stratifiers` | Value stratifiers (PAT-233) | Scenario 19's seven patients and proportion measure with three stratifiers on one group: the classic boolean `gender` (group-level) plus two **artifact-level value stratifiers** as the eCQM workspace builds them — `sex` (`Patient.gender.value`) and `age` (age bands 18-49 / 50-64 / 65+ at the end of the measurement period). Locks: artifact-level stratifiers reach the published `MeasureDefinition` and the evaluation at all (they used to get a CQL define and nothing else); a value stratifier's strata are the values (`female` / `male`, `65+`), not `true` / `false`; a FHIR primitive serialises as its value, not `FHIR.AdministrativeGender`; per-stratum populations and scores (`18-49` has an empty denominator and therefore no score); `exactStrata` — no extra `null` / type-name bucket appears. |
| `35-clause-coverage` | Clause-level coverage (PAT-232) | Publishes the scenario-32 CV measure and creates two test cases on it: a patient with two inpatient stays and a patient with no encounters at all. Runs the first in debug mode (`?debugMode=true`) and normally, the second in debug mode, then calls `POST /test-cases/coverage`. `assert-clause-coverage.sh` locks: a debug run carries `clauseCoverage` and it is internally consistent (statement totals add up, every clause has a parsable `line:col-line:col` locator, `coveredClauses` = clauses with `hits > 0`, `percent` derived from them), the patient with data covers **every** clause, a normal run has **no** `clauseCoverage` (population evaluation never pays for the per-node handler), the empty patient leaves at least one clause uncovered (per-encounter `where` / observation body never ran) over the **same** clause universe, and the measure-wide union is over the same CQL text, covers at least what each run covered, executed 2 / passed 2, with `hits` summed across runs. Needs cql-engine 5.x (`BreakpointHandler`). |
| `34-platform-value-set` | Platform value set (PAT-230) | A value set is created through `POST /api/value-sets` with one LOINC code and activated; an eCQM whose numerator is `exists [Observation: "Smoke HbA1c Orders"]` is published with the value set stored the way the picker stores it (`{name, oid: url}`). Locks: the generated CQL declares the **URL** (`valueset "…": 'https://…'` — it used to declare the name as the URL, so nothing could resolve it); evaluation gives IP=4 / Denom=4 / Numer=1 / 25% (a glucose Observation and a child's HbA1c must not count); version 2.0.0 adds a second code and is activated, and the **same** measure (unversioned reference → newest active) now gives Numer=2 / 50% with no restart; version 1.0.0 still holds exactly its own code; `$expand` and the value set search answer from the platform store (`source = platform`); the exchange package embeds the value set in full (version 2.0.0, both codes, conformance `source = platform`, no "could not be resolved" warning, an info that the reference is not pinned). Runs on PostgreSQL with V72 applied by Flyway and the RLS app role. |
| `33-measure-package-roundtrip` | Exchange package (PAT-229) | Publishes a proportion eCQM that `include`s an uploaded library, evaluates it, downloads the HL7 Quality Measure IG package (`GET /export/bundle` JSON + XML, `GET /export/conformance`), re-imports the JSON through `POST /import/bundle` with `Measure.version` rewritten to `importAsVersion` (same name + version is a duplicate), walks the import through `submit-for-review` + `approve`, and evaluates it. `assert.sh` locks IP=4 / Denom=3 / Numer=2 / 66.67 on the **imported** measure; `assert-measure-package.sh` locks the package shape (profiles claimed, absolute canonical `url`, `Measure.library` = primary Library `url\|version`, no `Measure.dataRequirement`, `text/cql-identifier` criteria, group / population ids, `cqfm-populationBasis`, improvement notation, both Libraries present with CQL, primary with ELM and `depends-on` for the included library + FHIRHelpers, packaged CQL decodes to the right `library` header, `fullUrl` = `url`, real FHIR XML), the conformance report (`exchangeReady`, no errors, same profiles as the Measure), the import result (`draft`, carries the CQL, the already-stored dependency is skipped) and that **both evaluations are identical**. First run of this scenario found that every `@RequestBody JsonNode` import endpoint returns 500 on Spring Boot 4.1 (Jackson 3 converter vs Jackson 2 trees; presumably since the 4.0 upgrade, not re-verified on older builds) — no test went through the HTTP layer. |
| `31-measure-status-guard` | Measure lifecycle guard (PAT-219 / BUG-143 / PAT-222) | Readiness-review Critical #2 lock. Creates a raw `MeasureDefinition` via `POST /api/measures` with **no** `ownerUsername` and asserts the GET body comes back with `ownerUsername = admin` (PAT-222: creator is stamped server-side) and `status = draft`; then PUTs the same body with `status = "active"` and asserts **HTTP 400** mentioning "review workflow" (PAT-222: status moves only through the workflow). Calls `$evaluate-measure` and asserts **HTTP 409** with `error = "Measure Not Evaluable"` — the refusal must happen before any FHIR/CQL work. Then `submit-for-review` + `approve` (→ `active`; BUG-143: works for a measure whose creator is the approver) and the identical evaluate call must return 200 with IP=4 / score=4.0 (4 of 6 seeded patients are adults at 2021-12-31). Proves the guard keys on lifecycle status, not on the eCQM publish path, and that approval lifts it without a restart. |
| `32-testcase-structured-expectations` | Structured test-case expectations (PAT-228) | Publishes an episode-based CV eCQM, then drives the **test-case API** instead of `$evaluate-measure`: creates a test case whose `expectedValues` lists per-group effective populations (`initial-population` 1, `measure-population` 1) and observation values `[2, 4]` for one patient with two inpatient stays, and asserts the run is **pass** with a `valueComparisons` table (and no legacy boolean table). Then PUTs the expectation to `[2, 5]` and asserts the run is **fail** with the observation row as the only mismatch, still reporting actual `[2, 4]`. Finally tries to save an expectation for a group the measure does not have and asserts **HTTP 400** naming the unknown group. Test cases evaluate an in-memory bundle against the current calendar year, so nothing is seeded into FHIR and `run.sh` substitutes `__YEAR__` in the bundle. |
| `30-authoring-library-reference` | CDS authoring → CQL generation | POSTs an `ArtifactRequest` whose `expTreeInclude` contains the exact `externalCqlElement` node shape the `LibraryDefinitionPicker` emits, then calls the generate-CQL endpoint. Asserts the generated CQL contains `include SharedLogic version '1.2.0' called shared` + the `\"shared\".\"HasDiabetes\"` body reference, and that no internal tree-node marker (`externalCqlElement`) leaks through. PAT-103 contract lock at the integration-smoke level. |

**eCQM scenarios also support** the following per-scenario flags in `expected.json`:

- `checkProvenance: true` (used by scenario `01`) — `GET /api/measures/{id}/reports` and validate the most recent row carries non-null `measureVersion` / `cqlHash` / `elmHash`. PAT-095 regression lock against publish-time ELM persistence (fixed when scenario 01 surfaced that `EcqmPublishService` was discarding the translator's ELM output).
- `idempotent: true` (used by scenario `01`) — re-runs `$evaluate-measure` with the same period and asserts the second run produces an identical score and identical population counts. Catches non-determinism: cache pollution, `MeasureReportBackfillService` duplicate inserts, ordering bugs in stratifier accumulation, etc.
- `stratifiers[]` (used by scenarios `19`, `36`) — array of `{strataId, expectedStrata: [{strataValue, populations, score?}], scoreTolerance?}`. Each entry asserts a stratifier produced the expected strata; strata are matched by `strataValue` (response order doesn't matter); `score` is optional per stratum (omit it for a stratum whose denominator is empty). Top-level `exactStrata: true` (PAT-233) additionally asserts each listed stratifier has **exactly** the listed strata — for value stratifiers, so no stray `null` / type-name bucket goes unnoticed.
- `supplementalDataDefinesPresent: ["SDE Sex", ...]` (used by scenario `19`) — fetches `/api/measures/{id}/export/cql` and greps for the listed `define "..."` headers. Light-touch: proves the SDE CQL branch ran + translator accepted it.
- `uploadLibrary: "library.cql"` (used by scenario `22`) — `POST /api/cql/libraries` with the named file's contents *before* save-and-publish, so the engine's `DatabaseLibrarySourceProvider` can resolve `include` statements at evaluate time.
- `checkCsvExport: {format, forbiddenLineStarts}` (used by scenario `23`) — after evaluate, fetches the latest report and exports as the named format (default `csv`); asserts no line in the body starts with any of the forbidden characters. Locks `CsvUtils.escapeCsv` end-to-end through the full export pipeline.
- `expectScoreNull: true` (used by scenario `24`) — asserts `groups[0].measureScore` is `null`. Use when IP/Denom is intentionally empty so the divide-by-zero / no-eligible-patients path returns null, not NaN/0.0/500. Mutually exclusive with top-level `score`.
- `groups: [{groupId, populations, score?, scoreTolerance?, stratifiers?}]` (used by scenarios `25`, `27`) — multi-group assertion. Each entry matches a response group by `groupId` and asserts populations + optional score. Optional `stratifiers[]` mirrors the top-level stratifier shape (`{strataId, expectedStrata: [...]}`) but scoped to the group — locks per-group stratifier results that BUG-474 follow-up enabled. Falls back to top-level `populations`/`score` for single-group scenarios.
- `maxEvaluationTimeMs: 8000` (used by scenarios `01`, `19`, `21`-`25`; `15000` for `26` concurrent) — wall-clock budget around the `$evaluate-measure` call. Run.sh times the evaluate call and exports `EVAL_ELAPSED_MS`; assert.sh fails the scenario if elapsed exceeds the budget. Catches N+1 query regressions and bulk-fetch slowdowns. Budget intentionally generous (typical eval runs in 1-2s) — guards against 4-8x regressions, not microbenchmarks.
- `concurrentEvaluations: N` (used by scenario `26`) — when set to ≥2, `run.sh` invokes `lib/evaluate-concurrent.sh` instead of `lib/evaluate.sh`. Fires N parallel `$evaluate-measure` calls and verifies all produce identical scores+populations before passing the first response to standard assertions. Catches concurrency bugs (pool starvation, thread-leak, race in saveReport) that single-call paths can't see.

**CDS scenario files**: `service.json` (CdsServiceConfigRequest), `invocation.json` (CdsRequest — hook + context + prefetch), `expected.json` with `type: \"cds-hook\"` plus `cardCount` / `cards[]` / `expectNoCards` / `debugPrefetchNonEmpty` / `debugErrorPhase` / `debugErrorRequiredFields` / `retrieveTracesCount` assertions. `run.sh` dispatches by the `type` field (default `ecqm`).

**CQL-execute scenario files**: `request.json` (CqlExecutionRequest — body posted verbatim to `/api/cql/execute`), `expected.json` with `type: \"cql-execute\"` plus `expectedHttpStatus` / `success` / `expressionTracesMinCount` / `expressionTraceRequiredFields` / `retrieveTracesMinCount` / `elmJsonNonEmpty` / `totalTimeMsPresent` / `errorInfoPhase` / `errorInfoRequiredFields` assertions. No FHIR seeding; pure debug-trace / error-contract test. `execute-cql.sh` emits `HTTP_STATUS\n---HTTP_STATUS_BODY---\nBODY` so both success and error paths flow through the same assertion script.

**Authoring-CQL scenario files**: `artifact.json` (ArtifactRequest — body posted verbatim to `/api/authoring/artifacts`), `expected.json` with `type: \"authoring-cql\"` plus `cqlContains` / `cqlDoesNotContain` string-match arrays. `generate-authoring-cql.sh` saves the artifact then calls `POST /artifacts/{id}/cql` — the response body (`{cql, warnings}`) is what `assert-authoring-cql.sh` checks. No FHIR seeding; pure authoring → CQL-generation contract.

**Platform-value-set scenario files** (PAT-230): `measure.json`, `bundle.json`, and `expected.json` with `type: "platform-value-set"`, `valueSet` (the `POST /api/value-sets` body for version 1), `nextVersion` (`version` + `concepts` of version 2), `populations` / `score` (asserted after version 1), `afterNextVersion` (`populations` / `score` asserted on the second evaluation) and `expectedCqlDeclaration`. `run.sh` does seed → create + activate v1 (`api-json.sh`) → `save-and-publish.sh` → `evaluate.sh` → new version + `PUT` codes + activate → `evaluate.sh` → `export-package.sh` + `$expand` + search, then `assert.sh` twice and `assert-platform-value-set.sh`. `lib/api-json.sh <METHOD> <path> [body.json]` is a generic authenticated JSON call (non-2xx fails) for future scenarios.

**Measure-package scenario files** (PAT-229): `measure.json` (eCQM artifact, as for `ecqm`), `bundle.json` (FHIR seed), optional `uploadLibrary`, `expected.json` with `type: "measure-package"`, the usual `populations` / `score` knobs (asserted on the imported measure), `importAsVersion`, `expectLibrariesSkipped`, and a `package` block: `measureProfiles[]`, `libraries[]` (first = primary), `dependsOn[]` (canonical-URL suffixes), `populationBasis`, `improvementNotation`. `run.sh` does seed → upload → `save-and-publish.sh` → `evaluate.sh` → `export-package.sh` → jq version rewrite → `import-package.sh` → `approve-measure.sh` → `evaluate.sh`, then `assert.sh` + `assert-measure-package.sh`. On failure the downloaded package is copied to `$SMOKE_LOG_DIR/<scenario>-package/`.

**Measure-status-guard scenario files** (PAT-219): `measure.json` (a raw `MeasureDefinition` — body posted verbatim to `POST /api/measures`, so it lands as `draft`; the eCQM publish path can't be used because publish always creates the definition as `active`), `bundle.json` (FHIR seed), `expected.json` with `type: \"measure-status-guard\"` plus `draftHttpStatus` (default 409) / `draftErrorField` / `draftMessageContains` / `approvedHttpStatus` (default 200) and the usual `populations` / `score` knobs for the post-approval result. `run.sh` does create → `evaluate-raw.sh` (draft phase) → `approve-measure.sh` (submit-for-review + approve) → `evaluate-raw.sh` again, then `assert-status-guard.sh` checks the refusal contract on the first envelope and forwards the second body to `assert.sh`.

**Clause-coverage scenario files** (PAT-232): `measure.json` (eCQM artifact, published via `save-and-publish.sh`), `bundle-with-encounters.json` / `bundle-no-encounters.json` (patient bundles stored ON the two test cases, `__YEAR__` placeholder as in scenario 32), `expected.json` with `type: "clause-coverage"`, `groupId`, and `withEncounters` / `noEncounters` blocks (`expectedPopulations` + `expectedObservations` for each test case, so both runs also pass their expectation). `run.sh` creates both test cases with `test-case-raw.sh`, runs debug / plain / debug, calls `/test-cases/coverage`, and hands the four envelopes to `assert-clause-coverage.sh`. On failure the envelopes are copied to `$SMOKE_LOG_DIR/<scenario>-files/`.

**Test-case-expectations scenario files** (PAT-228): `measure.json` (eCQM artifact, published via `save-and-publish.sh`), `testcase-bundle.json` (the patient bundle stored ON the test case; dates use the `__YEAR__` placeholder because test cases always evaluate against the current calendar year), `expected.json` with `type: \"test-case-expectations\"`, `groupId`, `expectedPopulations`, `expectedObservations` and `wrongObservations`. `run.sh` builds three request bodies with `jq`, calls `test-case-raw.sh` (create → run → update → run → create-with-unknown-group; every call returns the `HTTP_STATUS` envelope) and hands the envelopes to `assert-test-case-expectations.sh`.

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

# Skip the in-compose image build and run a prebuilt backend image instead.
# For CI (build once via buildx with a warm cache) and for machines whose
# TLS-inspecting proxy / antivirus breaks Maven inside the build container
# (PKIX errors): build the jar on the host, wrap it in eclipse-temurin:25-jre-alpine,
# tag it ghcr.io/lusnaker0730/cql/backend:<tag>, then:
SMOKE_SKIP_BUILD=1 BACKEND_IMAGE_TAG=<tag> scripts/smoke/run.sh

# Cold machine / CI runner: the backend's first boot (Flyway ~70 migrations +
# Spring context) can exceed the laptop-tuned 90s default. Raise per run:
SMOKE_BACKEND_HEALTH_TIMEOUT=300 SMOKE_FHIR_HEALTH_TIMEOUT=180 scripts/smoke/run.sh

# Keep the container logs (backend / hapi-fhir / postgres) after teardown —
# the only way to read a backend stack trace once the stack is gone:
SMOKE_LOG_DIR=./smoke-logs scripts/smoke/run.sh
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

### Auth and rate limits

- Every `lib/*.sh` call — including `invoke-cds.sh` — sends the seeded admin's
  JWT. Since BUG-139 there is no anonymous CDS invocation: the route is
  `permitAll` at the Spring Security layer, but `invokeService` authorizes the
  caller and answers an unauthenticated one with the same "not available" card
  as a missing service. Without the token every CDS scenario "passes" the HTTP
  call and fails its card assertions.
- `compose.override.yml` lifts all `RATE_LIMIT_*` ceilings for the backend
  (per-IP / per-user / per-tenant, plus the relaxed-binding
  `RATE_LIMIT_CDSINVOKERPM` / `RATE_LIMIT_AUTHRPM` that have no yml
  placeholder). The suite makes 150+ authenticated calls in a few minutes as
  one user from one IP; production defaults (user DEFAULT 40 RPM) 429'd
  scenarios 24–31 on the first CI run. Smoke-only — production keeps its
  limits.

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

## CI

`.github/workflows/smoke.yml` (PAT-220) runs this harness on every push to
`main` and on pull requests that touch `backend/`, `docker/`, `scripts/smoke/`
or the workflow itself (frontend-only / docs-only PRs print a notice and skip).
The job:

1. builds the backend image once with buildx (`cache-from/to: type=gha,scope=backend`,
   the same cache the Docker Build job populates), tagged `…/backend:smoke`;
2. writes a throwaway `docker/.env` (`openssl rand` values, nothing committed);
3. runs `run.sh` with `SMOKE_SKIP_BUILD=1 BACKEND_IMAGE_TAG=smoke`,
   `SMOKE_BACKEND_HEALTH_TIMEOUT=180 SMOKE_FHIR_HEALTH_TIMEOUT=180` and
   `SMOKE_LOG_DIR=smoke-logs`;
4. uploads `smoke-logs/` as the `smoke-stack-logs` artifact (7 days) on every
   run, so a red job still leaves the backend stack trace behind.

Budget ~8–12 min on a cold runner. Whether the job is a required status check
is a repository setting, not something the workflow decides.

## Exit codes

- `0` — all scenarios passed
- `1` — one or more scenarios failed (details per scenario on stderr)

## Known limitations

- First run is slow (~2 min) because Docker has to build the backend image.
  Subsequent runs with a warm cache finish in 60–90s.
- No cross-scenario ordering enforcement — scenarios must be self-contained.
- Doesn't cover frontend regressions. That needs Playwright; out of scope for
  this harness.
