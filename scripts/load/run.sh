#!/usr/bin/env bash
# PAT-266 — k6 load test for the CQL Platform: how big a clinic can one `$evaluate-measure` handle,
# and does the stack stay responsive while evaluations run?
#
# What it does
#   1. generates (once) a synthetic cohort with the frontend's TW Core patient generator (PAT-255):
#      scripts/load/data/cohort-<size>/ — chunked transaction bundles + the demo measure's oracle at
#      every checkpoint (500 / 1 000 / 2 000 / 5 000 / 10 000 patients)
#   2. brings up an isolated Docker stack (project `load`, ports 19xxx) with the PRODUCTION resource
#      limits from docker/docker-compose.yml (backend 2 CPU / 1.5 GB, HAPI 1 CPU / 2 GB — the VM's shape)
#   3. `scale` profile: seeds the cohort up to each checkpoint and runs k6 against `$evaluate-measure`
#      there; every answer must equal the oracle — scale is tested for correctness, not only speed
#   4. `concurrency` profile: at the largest checkpoint, concurrent evaluations + single-patient CQL
#      executions + browsing users for a few minutes, with thresholds on errors and latency
#   5. writes scripts/load/results/<timestamp>/ — k6 summaries, container logs, summary.md
#
# Usage
#   scripts/load/run.sh                         # 500 + 1 000 patients, then concurrency at 1 000 (~10–15 min)
#   LOAD_PATIENTS=10000 scripts/load/run.sh     # the whole ladder (seeding ~100 000 resources is most of the time)
#   LOAD_PROFILE=scale scripts/load/run.sh      # only the ladder; LOAD_PROFILE=concurrency for only the mix
#   scripts/load/run.sh --keep                  # leave the stack up afterwards
#   SMOKE_SKIP_BUILD=1 BACKEND_IMAGE_TAG=smoke-local scripts/load/run.sh   # prebuilt backend image (same knob as smoke)
#   LOAD_MEASURE_TIMEOUT_SECONDS=1800 …         # lift the production 120 s evaluation timeout to see the true duration
#   LOAD_BACKEND_CPUS=4 LOAD_HAPI_CPUS=2 …      # widen the production limits
#   LOAD_EVAL_VUS=3 LOAD_EXECUTE_VUS=5 LOAD_BROWSE_VUS=10 LOAD_DURATION=3m   # the concurrency mix
#   LOAD_K6_ENV_FILE=…                          # docker --env-file for k6 (LOAD_USER / LOAD_PASSWORD overrides)
#   LOAD_SEED_HAPI_CPUS=4 LOAD_SEED_PARALLEL=4  # HAPI's CPU limit and parallel transactions WHILE SEEDING only
#                                               # (HAPI is put back on LOAD_HAPI_CPUS before every k6 run)
#
# Requires: docker compose v2, jq, curl, bash; node + npm only when the cohort fixture is missing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SMOKE_DIR="$REPO_ROOT/scripts/smoke"
PROJECT_NAME="load"
COMPOSE="docker compose -p $PROJECT_NAME -f $REPO_ROOT/docker/docker-compose.yml -f $SMOKE_DIR/compose.override.yml -f $SCRIPT_DIR/compose.override.yml"

LOAD_BACKEND_PORT="${LOAD_BACKEND_PORT:-19080}"
LOAD_FHIR_PORT="${LOAD_FHIR_PORT:-19081}"
LOAD_PG_PORT="${LOAD_PG_PORT:-19432}"
export LOAD_BACKEND_PORT LOAD_FHIR_PORT LOAD_PG_PORT
LOAD_PATIENTS="${LOAD_PATIENTS:-1000}"            # largest checkpoint evaluated
LOAD_COHORT_SIZE="${LOAD_COHORT_SIZE:-10000}"     # fixture on disk; checkpoints up to LOAD_PATIENTS are used
LOAD_PROFILE="${LOAD_PROFILE:-scale,concurrency}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:1.3.0}"
SEED_PARALLEL="${LOAD_SEED_PARALLEL:-4}"
SEED_HAPI_CPUS="${LOAD_SEED_HAPI_CPUS:-$(nproc 2>/dev/null || echo 4)}"   # HAPI's CPU limit while seeding only
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RESULTS_DIR="${LOAD_RESULTS_DIR:-$SCRIPT_DIR/results/$RUN_ID}"
DATA_DIR="$SCRIPT_DIR/data/cohort-$LOAD_COHORT_SIZE"
MEASURE_NAME="DiabetesHbA1cRate"

# Same RLS app-role wiring as the smoke harness (PAT-223): the stack runs under real Row-Level Security.
if [ "${SMOKE_RLS_APP_ROLE:-1}" = "1" ]; then
    export DB_APP_USERNAME="${DB_APP_USERNAME:-cqlplatform_app}"
    export DB_APP_PASSWORD="${DB_APP_PASSWORD:-$(openssl rand -hex 16 2>/dev/null || date +%s%N)}"
    export TENANT_RLS_STRICT="${TENANT_RLS_STRICT:-true}"
fi
export API_BASE="http://localhost:${LOAD_BACKEND_PORT}/api"
export FHIR_BASE="http://localhost:${LOAD_FHIR_PORT}/fhir"

KEEP_STACK=0
for arg in "$@"; do
    case "$arg" in
        --keep) KEEP_STACK=1 ;;
        *) echo "unknown argument: $arg (only --keep is accepted; everything else is an environment variable)" >&2; exit 2 ;;
    esac
done

# Git Bash on Windows: docker needs a Windows path for -v, and must not rewrite /data-style arguments.
host_path() {
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) cygpath -w "$1" ;;
        *) printf '%s' "$1" ;;
    esac
}

cleanup() {
    if [ -d "$RESULTS_DIR" ]; then
        for svc in backend hapi-fhir; do
            $COMPOSE logs --no-color "$svc" > "$RESULTS_DIR/$svc.log" 2>&1 || true
        done
    fi
    if [ "$KEEP_STACK" -eq 0 ]; then
        echo ""
        echo "── Tearing down ──"
        $COMPOSE down -v --remove-orphans >/dev/null 2>&1 || true
    else
        echo ""
        echo "── Stack left running (--keep) — tear down with:"
        echo "    $COMPOSE down -v"
    fi
}
trap cleanup EXIT

check_port() {
    if (echo > /dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1; then
        echo "ERROR: port $1 is already in use ($2). Stop whatever is bound to it (another load / smoke run?)." >&2
        exit 1
    fi
}

echo "── Preflight ──"
for cmd in docker jq curl; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: '$cmd' not found on PATH." >&2; exit 1; }
done
check_port "$LOAD_BACKEND_PORT" backend
check_port "$LOAD_FHIR_PORT" hapi-fhir
check_port "$LOAD_PG_PORT" postgres
mkdir -p "$RESULTS_DIR"
echo "  results: $RESULTS_DIR"

echo ""
echo "── Cohort fixture ──"
if [ ! -f "$DATA_DIR/expected.json" ]; then
    echo "  $DATA_DIR is missing — generating it (frontend: npm run gen:load-cohort -- $LOAD_COHORT_SIZE)"
    command -v npm >/dev/null 2>&1 || { echo "ERROR: npm not found and the cohort fixture is missing." >&2; exit 1; }
    (cd "$REPO_ROOT/frontend" && npm run --silent gen:load-cohort -- "$LOAD_COHORT_SIZE")
    [ -f "$DATA_DIR/expected.json" ] || { echo "ERROR: fixture generation did not produce $DATA_DIR/expected.json" >&2; exit 1; }
fi
chunk_patients=$(jq -r '.chunkPatients' "$DATA_DIR/expected.json" | tr -d '\r')
chunk_count=$(jq -r '.chunkCount' "$DATA_DIR/expected.json" | tr -d '\r')
mapfile -t checkpoints < <(jq -r --argjson max "$LOAD_PATIENTS" '.checkpoints[] | select(.patients <= $max) | .patients' "$DATA_DIR/expected.json" | tr -d '\r')
[ "${#checkpoints[@]}" -gt 0 ] || { echo "ERROR: no checkpoint <= LOAD_PATIENTS=$LOAD_PATIENTS in $DATA_DIR/expected.json" >&2; exit 1; }
last_checkpoint="${checkpoints[${#checkpoints[@]}-1]}"
echo "  $(jq -r '.patientCount' "$DATA_DIR/expected.json" | tr -d '\r') patients in $chunk_count chunks of $chunk_patients; checkpoints: ${checkpoints[*]}"

echo ""
echo "── Bringing up stack (project: $PROJECT_NAME) ──"
BUILD_FLAG="--build"
if [ "${SMOKE_SKIP_BUILD:-0}" = "1" ]; then
    BUILD_FLAG=""
    echo "  SMOKE_SKIP_BUILD=1 — using prebuilt backend image tag '${BACKEND_IMAGE_TAG:-latest}'"
fi
echo "  limits: backend ${LOAD_BACKEND_CPUS:-2.0} CPU / ${LOAD_BACKEND_MEMORY:-1.5G}; HAPI ${LOAD_HAPI_CPUS:-1.0} CPU / ${LOAD_HAPI_MEMORY:-2G}; measure timeout ${LOAD_MEASURE_TIMEOUT_SECONDS:-120} s"
# shellcheck disable=SC2086
$COMPOSE up -d $BUILD_FLAG backend hapi-fhir postgres >/dev/null

echo ""
echo "── Waiting for services ──"
bash "$SMOKE_DIR/lib/wait-health.sh" "http://localhost:${LOAD_BACKEND_PORT}/actuator/health" "${LOAD_BACKEND_HEALTH_TIMEOUT:-240}"
bash "$SMOKE_DIR/lib/wait-health.sh" "http://localhost:${LOAD_FHIR_PORT}/fhir/metadata" "${LOAD_FHIR_HEALTH_TIMEOUT:-240}"
restarts=$(docker inspect --format '{{.RestartCount}}' "${PROJECT_NAME}-backend-1" 2>/dev/null || echo "?")
if [ "$restarts" != "0" ]; then
    echo "ERROR: backend restarted $restarts time(s) during boot (BUG-144 rule) — see $RESULTS_DIR/backend.log" >&2
    exit 1
fi
network=$(docker network ls --filter "name=^${PROJECT_NAME}_" --format '{{.Name}}' | head -1)
[ -n "$network" ] || { echo "ERROR: compose network for project $PROJECT_NAME not found" >&2; exit 1; }
backend_image=$(docker inspect --format '{{.Config.Image}}' "${PROJECT_NAME}-backend-1" 2>/dev/null || echo "?")
backend_revision=$(docker inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$backend_image" 2>/dev/null || true)

TOKEN=$(bash "$SMOKE_DIR/lib/auth.sh")
export TOKEN
measure_id=$(curl -sf "$API_BASE/measures?search=$MEASURE_NAME" -H "Authorization: Bearer $TOKEN" \
    | jq -r --arg n "$MEASURE_NAME" '[.[] | select(.name == $n)][0].id // empty' | tr -d '\r')
[ -n "$measure_id" ] || { echo "ERROR: seeded measure '$MEASURE_NAME' not found" >&2; exit 1; }
echo "  measure #$measure_id ($MEASURE_NAME), backend $backend_image (revision ${backend_revision:0:12})"

# ── seeding ──────────────────────────────────────────────────────────────────────────────────────
seeded_chunks=0
SEED_MS=0
seed_to() {   # seed_to <patients>: PUT every chunk up to the checkpoint that is not in HAPI yet
    local target_patients="$1"
    local target_chunks=$(( target_patients / chunk_patients ))
    local start_ns end_ns
    start_ns=$(date +%s%N)
    if [ "$target_chunks" -gt "$seeded_chunks" ]; then
        echo "  seeding chunks $((seeded_chunks + 1))..$target_chunks ($SEED_PARALLEL in parallel, HAPI at $SEED_HAPI_CPUS CPU)"
        # Ingesting ~1 000 resources per transaction on HAPI's production 1-CPU limit takes minutes per
        # chunk; a 10 000-patient cohort would take hours. Seeding is setup, not the thing measured, so
        # HAPI gets more CPU while it ingests and is put back on the production limit before k6 runs.
        docker update --cpus "$SEED_HAPI_CPUS" "${PROJECT_NAME}-hapi-fhir-1" >/dev/null
        seq $((seeded_chunks + 1)) "$target_chunks" \
            | xargs -P "$SEED_PARALLEL" -I{} bash -c 'f=$(printf "%s/chunk-%04d.json" "$1" "$2"); bash "$3/lib/seed-fhir.sh" "$f" >/dev/null || { echo "    seed failed: $f" >&2; exit 1; }' _ "$DATA_DIR" {} "$SMOKE_DIR"
        docker update --cpus "${LOAD_HAPI_CPUS:-1.0}" "${PROJECT_NAME}-hapi-fhir-1" >/dev/null
        seeded_chunks=$target_chunks
    fi
    end_ns=$(date +%s%N)
    SEED_MS=$(( (end_ns - start_ns) / 1000000 ))
    local total
    total=$(curl -sf "$FHIR_BASE/Patient?_summary=count" | jq -r '.total' | tr -d '\r')
    [ "$total" = "$target_patients" ] || { echo "ERROR: FHIR holds $total patients, expected $target_patients" >&2; exit 1; }
    echo "  FHIR holds $total patients (seeded in ${SEED_MS} ms)"
}

# ── k6 ───────────────────────────────────────────────────────────────────────────────────────────
run_k6() {   # run_k6 <profile> <checkpoint>
    local profile="$1" checkpoint="$2"
    local env_file_args=()
    [ -n "${LOAD_K6_ENV_FILE:-}" ] && env_file_args=(--env-file "$LOAD_K6_ENV_FILE")
    echo "  k6 $profile @ $checkpoint patients"
    MSYS_NO_PATHCONV=1 docker run --rm -i --network "$network" \
        -v "$(host_path "$DATA_DIR"):/data:ro" \
        -v "$(host_path "$SCRIPT_DIR/k6"):/scripts:ro" \
        -v "$(host_path "$RESULTS_DIR"):/results" \
        -e API_BASE=http://backend:8080/api -e FHIR_BASE=http://hapi-fhir:8080/fhir \
        -e PROFILE="$profile" -e CHECKPOINT="$checkpoint" -e EXPECTED=/data/expected.json \
        -e ITERATIONS="${LOAD_ITERATIONS:-2}" -e EVAL_VUS="${LOAD_EVAL_VUS:-3}" -e EXECUTE_VUS="${LOAD_EXECUTE_VUS:-5}" \
        -e BROWSE_VUS="${LOAD_BROWSE_VUS:-10}" -e DURATION="${LOAD_DURATION:-3m}" -e EVAL_TIMEOUT="${LOAD_EVAL_TIMEOUT:-45m}" \
        "${env_file_args[@]}" \
        "$K6_IMAGE" run --no-usage-report --summary-export="/results/${profile}-${checkpoint}.json" /scripts/evaluate-measure.js \
        2>&1 | tee "$RESULTS_DIR/${profile}-${checkpoint}.log"
    return "${PIPESTATUS[0]}"
}

metric() {   # metric <summary.json> <metric> <stat>  → value or "n/a"
    jq -r --arg m "$2" --arg s "$3" '.metrics[$m][$s] // "n/a" | if type == "number" then (. * 10 | round / 10) else . end' "$1" 2>/dev/null || echo "n/a"
}

count_in_log() {   # count_in_log <pattern>
    $COMPOSE logs --no-color backend 2>/dev/null | grep -c -- "$1" || true
}

failed=0
scale_rows=()
concurrency_row=""

if [[ ",$LOAD_PROFILE," == *",scale,"* ]]; then
    echo ""
    echo "── Scale ladder ──"
    for cp in "${checkpoints[@]}"; do
        seed_to "$cp"
        status="pass"
        run_k6 scale "$cp" || { status="FAIL"; failed=1; }
        summary="$RESULTS_DIR/scale-${cp}.json"
        resources=$(jq -r --argjson n "$cp" '.checkpoints[] | select(.patients == $n) | .resources' "$DATA_DIR/expected.json" | tr -d '\r')
        scale_rows+=("| $cp | $resources | $(( SEED_MS / 1000 )) s | $(metric "$summary" evaluate_measure_ms avg) | $(metric "$summary" evaluate_measure_ms med) | $(metric "$summary" evaluate_measure_ms max) | $(metric "$summary" checks passes) / $(metric "$summary" checks fails) | $status |")
    done
else
    echo ""
    echo "── Seeding to $last_checkpoint patients ──"
    seed_to "$last_checkpoint"
fi

if [[ ",$LOAD_PROFILE," == *",concurrency,"* ]]; then
    echo ""
    echo "── Concurrency mix @ $last_checkpoint patients ──"
    status="pass"
    run_k6 concurrency "$last_checkpoint" || { status="FAIL"; failed=1; }
    summary="$RESULTS_DIR/concurrency-${last_checkpoint}.json"
    concurrency_row="| $last_checkpoint | ${LOAD_EVAL_VUS:-3} / ${LOAD_EXECUTE_VUS:-5} / ${LOAD_BROWSE_VUS:-10} | ${LOAD_DURATION:-3m} | $(metric "$summary" evaluate_measure_ms med) | $(metric "$summary" evaluate_measure_ms max) | $(metric "$summary" 'http_req_duration{scenario:cql_execute}' 'p(95)') | $(metric "$summary" 'http_req_duration{scenario:browse}' 'p(95)') | $(metric "$summary" http_req_failed value) | $(metric "$summary" checks passes) / $(metric "$summary" checks fails) | $status |"
fi

# ── summary ──────────────────────────────────────────────────────────────────────────────────────
timeouts=$(count_in_log 'Measure evaluation timed out')
bulk_failures=$(count_in_log 'Bulk fetch failed')
error_lines=$(count_in_log '"level":"ERROR"')
{
    echo "# Load test $RUN_ID"
    echo ""
    echo "- backend image: \`$backend_image\` (revision \`${backend_revision:-?}\`), repo HEAD \`$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo '?')\`"
    echo "- limits: backend ${LOAD_BACKEND_CPUS:-2.0} CPU / ${LOAD_BACKEND_MEMORY:-1.5G}; HAPI ${LOAD_HAPI_CPUS:-1.0} CPU / ${LOAD_HAPI_MEMORY:-2G}; measure timeout ${LOAD_MEASURE_TIMEOUT_SECONDS:-120} s; host $(uname -s) $(nproc 2>/dev/null || echo '?') cpu"
    echo "- cohort: \`$DATA_DIR\` (seed $(jq -r '.seed' "$DATA_DIR/expected.json" | tr -d '\r'), $chunk_patients patients per chunk); profile: $LOAD_PROFILE"
    echo ""
    if [ "${#scale_rows[@]}" -gt 0 ]; then
        echo "## Scale: one \`\$evaluate-measure\` over the whole cohort (${LOAD_ITERATIONS:-2} iterations each)"
        echo ""
        echo "| patients | resources | seeding | evaluate avg ms | median ms | max ms | checks pass / fail | result |"
        echo "|---|---|---|---|---|---|---|---|"
        printf '%s\n' "${scale_rows[@]}"
        echo ""
    fi
    if [ -n "$concurrency_row" ]; then
        echo "## Concurrency: evaluations + CQL executions + browsing at once"
        echo ""
        echo "| patients | VUs eval / execute / browse | duration | evaluate median ms | max ms | execute p95 ms | browse p95 ms | http failed rate | checks pass / fail | result |"
        echo "|---|---|---|---|---|---|---|---|---|---|"
        echo "$concurrency_row"
        echo ""
    fi
    echo "## Backend log signals"
    echo ""
    echo "- evaluation timeouts (\`Measure evaluation timed out\`): $timeouts"
    echo "- bulk fetch failures (\`Bulk fetch failed\`): $bulk_failures"
    echo "- ERROR log lines: $error_lines"
} > "$RESULTS_DIR/summary.md"

echo ""
cat "$RESULTS_DIR/summary.md"
echo ""
if [ "$failed" -ne 0 ]; then
    echo "LOAD TEST FAILED — see $RESULTS_DIR"
    exit 1
fi
echo "LOAD TEST PASSED — $RESULTS_DIR"
