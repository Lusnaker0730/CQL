#!/usr/bin/env bash
# CQL Platform local integration smoke test harness.
#
# Spins up a minimal isolated Docker stack (backend + postgres + hapi-fhir), runs
# every scenario under scripts/smoke/scenarios/ through a save → publish →
# evaluate pipeline, and asserts the response matches each scenario's
# expected.json. Designed to run before `git push` as a sanity check that the
# full stack still behaves — unit tests alone missed the #230/#239/#247 family
# of integration regressions (see code-review item #11).
#
# Runtime budget: ~60s on a warm Docker cache. Scenarios run serially on a
# shared stack; isolation is via disjoint Measurement Periods per scenario
# (each scenario's fixture data lives only in its own period window).
#
# Usage:
#   scripts/smoke/run.sh                 # run all scenarios
#   scripts/smoke/run.sh --keep          # don't tear down on success (debugging)
#   scripts/smoke/run.sh 01-proportion*  # glob-filter scenarios by name
#
# Requires: docker compose v2, jq, curl, bash.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT_NAME="smoke"
COMPOSE="docker compose -p $PROJECT_NAME -f $REPO_ROOT/docker/docker-compose.yml -f $SCRIPT_DIR/compose.override.yml"

# Ports are in the 18xxx range by default to avoid colliding with dev stacks on
# 8080/8081/5432. Override via env if you need something different:
#   SMOKE_BACKEND_PORT=28080 scripts/smoke/run.sh
export SMOKE_BACKEND_PORT="${SMOKE_BACKEND_PORT:-18080}"
export SMOKE_FHIR_PORT="${SMOKE_FHIR_PORT:-18081}"
export SMOKE_PG_PORT="${SMOKE_PG_PORT:-18432}"

# Env wired to lib/*.sh via export
export API_BASE="${API_BASE:-http://localhost:${SMOKE_BACKEND_PORT}/api}"
export FHIR_BASE="${FHIR_BASE:-http://localhost:${SMOKE_FHIR_PORT}/fhir}"

KEEP_STACK=0
SCENARIO_GLOB="*"
for arg in "$@"; do
    case "$arg" in
        --keep) KEEP_STACK=1 ;;
        *) SCENARIO_GLOB="$arg" ;;
    esac
done

cleanup() {
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

# Preflight: refuse to run if a dev stack is already bound to the ports we need
check_port() {
    local port="$1"
    local name="$2"
    if (echo > /dev/tcp/127.0.0.1/"$port") >/dev/null 2>&1; then
        echo "ERROR: port $port is already in use ($name). Stop your dev stack before running smoke." >&2
        exit 1
    fi
}

echo "── Preflight ──"
for cmd in docker jq curl; do
    command -v "$cmd" >/dev/null 2>&1 || {
        echo "ERROR: '$cmd' not found on PATH. Install it first." >&2
        echo "  Windows: winget install jqlang.jq (for jq); Docker Desktop for docker" >&2
        echo "  macOS:   brew install jq docker curl" >&2
        echo "  Linux:   apt-get install -y jq docker.io curl" >&2
        exit 1
    }
done
check_port "$SMOKE_BACKEND_PORT" "backend"
check_port "$SMOKE_FHIR_PORT" "hapi-fhir"
check_port "$SMOKE_PG_PORT" "postgres"

echo ""
echo "── Bringing up stack (project: $PROJECT_NAME) ──"
$COMPOSE up -d --build backend hapi-fhir postgres >/dev/null

echo ""
echo "── Waiting for services ──"
bash "$SCRIPT_DIR/lib/wait-health.sh" "http://localhost:${SMOKE_BACKEND_PORT}/actuator/health" 90
bash "$SCRIPT_DIR/lib/wait-health.sh" "http://localhost:${SMOKE_FHIR_PORT}/fhir/metadata" 60

echo ""
echo "── Authenticating ──"
TOKEN=$(bash "$SCRIPT_DIR/lib/auth.sh")
export TOKEN
echo "  got JWT (${#TOKEN} chars)"

echo ""
echo "── Running scenarios ──"
failed_scenarios=()
passed_scenarios=()

for scenario_dir in "$SCRIPT_DIR/scenarios/"$SCENARIO_GLOB/; do
    [ -d "$scenario_dir" ] || continue
    name=$(basename "$scenario_dir")
    echo ""
    echo "  ── $name ──"

    measure_file="$scenario_dir/measure.json"
    bundle_file="$scenario_dir/bundle.json"
    expected_file="$scenario_dir/expected.json"

    for f in "$measure_file" "$bundle_file" "$expected_file"; do
        if [ ! -f "$f" ]; then
            echo "    ✗ missing $f" >&2
            failed_scenarios+=("$name")
            continue 2
        fi
    done

    period_start=$(jq -r '.periodStart' "$expected_file")
    period_end=$(jq -r '.periodEnd' "$expected_file")

    # Reset FHIR state so scenarios can't see each other's patients — pure-demographic
    # criteria (AgeRange, Gender) don't self-filter by measurement period so without this
    # scenario N would match scenario N-1's patients.
    if ! bash "$SCRIPT_DIR/lib/reset-fhir.sh"; then
        failed_scenarios+=("$name")
        continue
    fi

    if ! bash "$SCRIPT_DIR/lib/seed-fhir.sh" "$bundle_file"; then
        failed_scenarios+=("$name")
        continue
    fi

    if ! measure_id=$(bash "$SCRIPT_DIR/lib/save-and-publish.sh" "$measure_file"); then
        failed_scenarios+=("$name")
        continue
    fi

    if ! response=$(bash "$SCRIPT_DIR/lib/evaluate.sh" "$measure_id" "$period_start" "$period_end"); then
        failed_scenarios+=("$name")
        continue
    fi

    if echo "$response" | bash "$SCRIPT_DIR/lib/assert.sh" - "$expected_file"; then
        passed_scenarios+=("$name")
    else
        failed_scenarios+=("$name")
    fi
done

echo ""
echo "── Results ──"
echo "  passed: ${#passed_scenarios[@]}"
echo "  failed: ${#failed_scenarios[@]}"

if [ "${#failed_scenarios[@]}" -gt 0 ]; then
    echo ""
    for s in "${failed_scenarios[@]}"; do
        echo "  ✗ $s"
    done
    exit 1
fi

echo ""
echo "  ✓ all scenarios pass"
