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
#   SMOKE_SKIP_BUILD=1 BACKEND_IMAGE_TAG=smoke-local scripts/smoke/run.sh
#                                        # use a prebuilt backend image (CI / host-built jar)
#   SMOKE_BACKEND_HEALTH_TIMEOUT=300 SMOKE_FHIR_HEALTH_TIMEOUT=180 scripts/smoke/run.sh
#                                        # cold machine / CI runner (defaults 90 / 60)
#   SMOKE_LOG_DIR=./smoke-logs scripts/smoke/run.sh
#                                        # dump container logs before teardown
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

# PAT-223: run the backend as the least-privilege app role so the whole suite executes
# under REAL Row-Level Security (postgres-init/10-app-role.sh creates the role on the
# fresh tmpfs volume; docker-compose passes DB_APP_* to both containers). A throwaway
# password per run — nothing is committed. Set SMOKE_RLS_APP_ROLE=0 to run as the owner
# (RLS bypassed) when bisecting a failure.
if [ "${SMOKE_RLS_APP_ROLE:-1}" = "1" ]; then
    export DB_APP_USERNAME="${DB_APP_USERNAME:-cqlplatform_app}"
    export DB_APP_PASSWORD="${DB_APP_PASSWORD:-$(openssl rand -hex 16 2>/dev/null || date +%s%N)}"
    export TENANT_RLS_STRICT="${TENANT_RLS_STRICT:-true}"
fi

# Env wired to lib/*.sh via export
export API_BASE="${API_BASE:-http://localhost:${SMOKE_BACKEND_PORT}/api}"
export FHIR_BASE="${FHIR_BASE:-http://localhost:${SMOKE_FHIR_PORT}/fhir}"
# CDS invocation endpoint is at /cds-services (NOT under /api) — per CDS Hooks spec
# it's a separate public namespace for EHRs to call.
export CDS_BASE="${CDS_BASE:-http://localhost:${SMOKE_BACKEND_PORT}/cds-services}"

KEEP_STACK=0
SCENARIO_GLOB="*"
for arg in "$@"; do
    case "$arg" in
        --keep) KEEP_STACK=1 ;;
        *) SCENARIO_GLOB="$arg" ;;
    esac
done

cleanup() {
    # SMOKE_LOG_DIR: dump container logs before teardown so a failed run can be
    # diagnosed afterwards (CI uploads the directory as an artifact). The stack
    # is gone by the time anyone reads the job output, so this is the only
    # place the backend's stack trace survives.
    if [ -n "${SMOKE_LOG_DIR:-}" ]; then
        mkdir -p "$SMOKE_LOG_DIR"
        for svc in backend hapi-fhir postgres; do
            $COMPOSE logs --no-color "$svc" > "$SMOKE_LOG_DIR/$svc.log" 2>&1 || true
        done
        echo ""
        echo "── Container logs written to $SMOKE_LOG_DIR ──"
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
# SMOKE_SKIP_BUILD=1 skips the in-compose backend image build and runs whatever
# ghcr.io/lusnaker0730/cql/backend:${BACKEND_IMAGE_TAG:-latest} is already loaded.
# Two users: CI (builds once via buildx with a warm GHA cache, then runs the
# harness against it) and machines whose TLS-inspecting proxy / antivirus breaks
# Maven inside the build container (PKIX errors) — build the jar on the host,
# wrap it in the runtime image, tag it, and point BACKEND_IMAGE_TAG at it.
BUILD_FLAG="--build"
if [ "${SMOKE_SKIP_BUILD:-0}" = "1" ]; then
    BUILD_FLAG=""
    echo "  SMOKE_SKIP_BUILD=1 — using prebuilt backend image tag '${BACKEND_IMAGE_TAG:-latest}'"
fi
# shellcheck disable=SC2086  # BUILD_FLAG is intentionally word-split (empty or --build)
$COMPOSE up -d $BUILD_FLAG backend hapi-fhir postgres >/dev/null

echo ""
echo "── Waiting for services ──"
# Defaults are tuned for a warm laptop. A cold machine or CI runner (Flyway's
# ~70 migrations + Spring context + HAPI JPA init on a fresh volume) can need
# 2-4 min for the backend alone — override per environment rather than editing.
bash "$SCRIPT_DIR/lib/wait-health.sh" "http://localhost:${SMOKE_BACKEND_PORT}/actuator/health" "${SMOKE_BACKEND_HEALTH_TIMEOUT:-90}"
bash "$SCRIPT_DIR/lib/wait-health.sh" "http://localhost:${SMOKE_FHIR_PORT}/fhir/metadata" "${SMOKE_FHIR_HEALTH_TIMEOUT:-60}"

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

    expected_file="$scenario_dir/expected.json"
    if [ ! -f "$expected_file" ]; then
        echo "    ✗ missing $expected_file" >&2
        failed_scenarios+=("$name")
        continue
    fi

    # Scenario type dispatch: eCQM (default) uses measure.json + bundle.json with
    # save-and-publish + evaluate; cds-hook uses service.json + bundle.json + invocation.json
    # with save-cds-service + invoke-cds. Both always reset FHIR first.
    scenario_type=$(jq -r '.type // "ecqm"' "$expected_file" | tr -d '\r')

    # Reset FHIR state so scenarios can't see each other's resources. Pure-demographic
    # criteria (AgeRange, Gender) don't self-filter by measurement period, and CDS
    # prefetch queries don't filter by period either — without reset, scenario N sees
    # N-1's data.
    if ! bash "$SCRIPT_DIR/lib/reset-fhir.sh"; then
        failed_scenarios+=("$name")
        continue
    fi

    case "$scenario_type" in
        ecqm)
            measure_file="$scenario_dir/measure.json"
            bundle_file="$scenario_dir/bundle.json"
            for f in "$measure_file" "$bundle_file"; do
                if [ ! -f "$f" ]; then
                    echo "    ✗ missing $f" >&2
                    failed_scenarios+=("$name")
                    continue 2
                fi
            done
            period_start=$(jq -r '.periodStart' "$expected_file")
            period_end=$(jq -r '.periodEnd' "$expected_file")

            if ! bash "$SCRIPT_DIR/lib/seed-fhir.sh" "$bundle_file"; then
                failed_scenarios+=("$name"); continue
            fi
            # Optional: upload an external CQL library before publish so the
            # engine's DatabaseLibrarySourceProvider can resolve `include`
            # statements. Triggered when expected.json carries `uploadLibrary`.
            upload_lib=$(jq -r '.uploadLibrary // empty' "$expected_file" | tr -d '\r')
            if [ -n "$upload_lib" ]; then
                if ! bash "$SCRIPT_DIR/lib/upload-library.sh" "$scenario_dir/$upload_lib"; then
                    failed_scenarios+=("$name"); continue
                fi
            fi
            if ! measure_id=$(bash "$SCRIPT_DIR/lib/save-and-publish.sh" "$measure_file"); then
                failed_scenarios+=("$name"); continue
            fi
            # Wall-clock the evaluate call so assert.sh can enforce a per-scenario
            # `maxEvaluationTimeMs` budget. Catches N+1 query regressions and
            # bulk-fetch slowdowns that don't change correctness but affect prod cost.
            eval_start_ns=$(date +%s%N)
            # Optional concurrent-eval stress: when expected.json carries
            # `concurrentEvaluations: N` (>=2), fire N parallel evaluates and
            # assert all produce identical scores/populations before passing the
            # first response on to standard assertions.
            concurrency=$(jq -r '.concurrentEvaluations // empty' "$expected_file" | tr -d '\r')
            if [ -n "$concurrency" ] && [ "$concurrency" -ge 2 ] 2>/dev/null; then
                if ! response=$(bash "$SCRIPT_DIR/lib/evaluate-concurrent.sh" \
                        "$measure_id" "$period_start" "$period_end" "$concurrency"); then
                    failed_scenarios+=("$name"); continue
                fi
            else
                if ! response=$(bash "$SCRIPT_DIR/lib/evaluate.sh" "$measure_id" "$period_start" "$period_end"); then
                    failed_scenarios+=("$name"); continue
                fi
            fi
            eval_end_ns=$(date +%s%N)
            EVAL_ELAPSED_MS=$(( (eval_end_ns - eval_start_ns) / 1000000 ))
            export EVAL_ELAPSED_MS
            if echo "$response" | bash "$SCRIPT_DIR/lib/assert.sh" - "$expected_file" "$measure_id"; then
                passed_scenarios+=("$name")
            else
                failed_scenarios+=("$name")
            fi
            ;;

        cds-hook)
            service_file="$scenario_dir/service.json"
            invocation_file="$scenario_dir/invocation.json"
            bundle_file="$scenario_dir/bundle.json"  # optional for CDS (prefetch inline supported)
            for f in "$service_file" "$invocation_file"; do
                if [ ! -f "$f" ]; then
                    echo "    ✗ missing $f" >&2
                    failed_scenarios+=("$name")
                    continue 2
                fi
            done

            # CDS: optional bundle seeding (when scenario needs FHIR server for non-prefetched resolution)
            if [ -f "$bundle_file" ]; then
                if ! bash "$SCRIPT_DIR/lib/seed-fhir.sh" "$bundle_file"; then
                    failed_scenarios+=("$name"); continue
                fi
            fi

            # Save: some scenarios (14-disabled) need to test invocation of a disabled
            # service. `expectInvocationError` in expected.json tells us to treat a
            # 404/4xx from invoke-cds as a PASS rather than a failure.
            if ! service_id=$(bash "$SCRIPT_DIR/lib/save-cds-service.sh" "$service_file"); then
                failed_scenarios+=("$name"); continue
            fi

            expect_error=$(jq -r '.expectInvocationError // false' "$expected_file" | tr -d '\r')
            if [ "$expect_error" = "true" ]; then
                # Intentionally expect non-200 — swallow the error-exit from invoke-cds.
                if response=$(bash "$SCRIPT_DIR/lib/invoke-cds.sh" "$service_id" "$invocation_file" 2>&1); then
                    echo "    ✗ expected invocation error (scenario asserted expectInvocationError=true) but got 200" >&2
                    failed_scenarios+=("$name")
                else
                    echo "    ✓ invocation correctly rejected"
                    passed_scenarios+=("$name")
                fi
            else
                if ! response=$(bash "$SCRIPT_DIR/lib/invoke-cds.sh" "$service_id" "$invocation_file"); then
                    failed_scenarios+=("$name"); continue
                fi
                if echo "$response" | bash "$SCRIPT_DIR/lib/assert-cds.sh" - "$expected_file"; then
                    passed_scenarios+=("$name")
                else
                    failed_scenarios+=("$name")
                fi
            fi
            ;;

        cql-execute)
            # Debug-mode smoke for POST /api/cql/execute. Scenario contributes
            # a pre-built request.json (CQL body + flags); assertion is on
            # debugTrace field presence, NOT evaluation semantics.
            request_file="$scenario_dir/request.json"
            if [ ! -f "$request_file" ]; then
                echo "    ✗ missing $request_file" >&2
                failed_scenarios+=("$name")
                continue
            fi
            if ! response=$(bash "$SCRIPT_DIR/lib/execute-cql.sh" "$request_file"); then
                failed_scenarios+=("$name"); continue
            fi
            if echo "$response" | bash "$SCRIPT_DIR/lib/assert-cql-debug.sh" - "$expected_file"; then
                passed_scenarios+=("$name")
            else
                failed_scenarios+=("$name")
            fi
            ;;

        authoring-cql)
            # CDS authoring → CQL generation smoke (PAT-103 LibraryDefinitionPicker
            # contract at integration level). Scenario POSTs an ArtifactRequest
            # body that matches what the frontend picker produces, then calls
            # the generate-CQL endpoint. Assertion is substring-based on the
            # generated CQL so we lock both the include statement and the body
            # reference without being brittle on unrelated whitespace/comments.
            artifact_file="$scenario_dir/artifact.json"
            if [ ! -f "$artifact_file" ]; then
                echo "    ✗ missing $artifact_file" >&2
                failed_scenarios+=("$name")
                continue
            fi
            if ! response=$(bash "$SCRIPT_DIR/lib/generate-authoring-cql.sh" "$artifact_file"); then
                failed_scenarios+=("$name"); continue
            fi
            if echo "$response" | bash "$SCRIPT_DIR/lib/assert-authoring-cql.sh" - "$expected_file"; then
                passed_scenarios+=("$name")
            else
                failed_scenarios+=("$name")
            fi
            ;;

        measure-status-guard)
            # PAT-219 lifecycle guard. A raw MeasureDefinition (POST /api/measures —
            # NOT the eCQM publish path, which always lands as `active`) is created
            # as draft, evaluated (must be refused with 409), walked through
            # submit-for-review + approve, then evaluated again (must succeed with
            # the real cohort result). Locks that the guard keys on lifecycle
            # status and that the approval workflow lifts it without a restart.
            measure_file="$scenario_dir/measure.json"
            bundle_file="$scenario_dir/bundle.json"
            for f in "$measure_file" "$bundle_file"; do
                if [ ! -f "$f" ]; then
                    echo "    ✗ missing $f" >&2
                    failed_scenarios+=("$name")
                    continue 2
                fi
            done
            period_start=$(jq -r '.periodStart' "$expected_file" | tr -d '\r')
            period_end=$(jq -r '.periodEnd' "$expected_file" | tr -d '\r')

            if ! bash "$SCRIPT_DIR/lib/seed-fhir.sh" "$bundle_file"; then
                failed_scenarios+=("$name"); continue
            fi
            if ! measure_id=$(bash "$SCRIPT_DIR/lib/create-measure-definition.sh" "$measure_file"); then
                failed_scenarios+=("$name"); continue
            fi
            guard_tmp=$(mktemp -d)
            # PAT-222: the creator must come back as ownerUsername (server-stamped, the
            # request body sent none), and a PUT that flips the lifecycle status must be
            # refused — otherwise the review workflow the PAT-219 guard trusts is bypassable.
            if ! bash "$SCRIPT_DIR/lib/get-measure.sh" "$measure_id" > "$guard_tmp/measure.json"; then
                rm -rf "$guard_tmp"; failed_scenarios+=("$name"); continue
            fi
            jq '.status = "active"' "$guard_tmp/measure.json" > "$guard_tmp/status-edit.json"
            if ! bash "$SCRIPT_DIR/lib/update-measure-raw.sh" "$measure_id" "$guard_tmp/status-edit.json" > "$guard_tmp/status-edit.raw"; then
                rm -rf "$guard_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/evaluate-raw.sh" "$measure_id" "$period_start" "$period_end" > "$guard_tmp/draft.raw"; then
                rm -rf "$guard_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/approve-measure.sh" "$measure_id"; then
                rm -rf "$guard_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/evaluate-raw.sh" "$measure_id" "$period_start" "$period_end" > "$guard_tmp/approved.raw"; then
                rm -rf "$guard_tmp"; failed_scenarios+=("$name"); continue
            fi
            if bash "$SCRIPT_DIR/lib/assert-status-guard.sh" "$guard_tmp/draft.raw" "$guard_tmp/approved.raw" "$expected_file" \
                    "$guard_tmp/measure.json" "$guard_tmp/status-edit.raw"; then
                passed_scenarios+=("$name")
            else
                failed_scenarios+=("$name")
            fi
            rm -rf "$guard_tmp"
            ;;

        measure-package)
            # PAT-229 exchange-package round trip: publish → evaluate → export the
            # HL7 Quality Measure IG package (JSON, XML, conformance report) →
            # import it back as a new version → approve → evaluate. The imported
            # measure must compute what the exported one does; before PAT-229 it
            # arrived without its CQL and could not be evaluated at all.
            measure_file="$scenario_dir/measure.json"
            bundle_file="$scenario_dir/bundle.json"
            for f in "$measure_file" "$bundle_file"; do
                if [ ! -f "$f" ]; then
                    echo "    ✗ missing $f" >&2
                    failed_scenarios+=("$name")
                    continue 2
                fi
            done
            period_start=$(jq -r '.periodStart' "$expected_file" | tr -d '\r')
            period_end=$(jq -r '.periodEnd' "$expected_file" | tr -d '\r')
            import_version=$(jq -r '.importAsVersion' "$expected_file" | tr -d '\r')

            if ! bash "$SCRIPT_DIR/lib/seed-fhir.sh" "$bundle_file"; then
                failed_scenarios+=("$name"); continue
            fi
            upload_lib=$(jq -r '.uploadLibrary // empty' "$expected_file" | tr -d '\r')
            if [ -n "$upload_lib" ]; then
                if ! bash "$SCRIPT_DIR/lib/upload-library.sh" "$scenario_dir/$upload_lib"; then
                    failed_scenarios+=("$name"); continue
                fi
            fi
            if ! measure_id=$(bash "$SCRIPT_DIR/lib/save-and-publish.sh" "$measure_file"); then
                failed_scenarios+=("$name"); continue
            fi
            pkg_tmp=$(mktemp -d)
            if ! bash "$SCRIPT_DIR/lib/evaluate.sh" "$measure_id" "$period_start" "$period_end" > "$pkg_tmp/original.json"; then
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/export-package.sh" "$measure_id" "$pkg_tmp"; then
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            # Same name + version would be refused as a duplicate: import it as the
            # "next version the other organisation sent us".
            jq --arg v "$import_version" \
                '(.entry[].resource | select(.resourceType == "Measure") | .version) = $v' \
                "$pkg_tmp/bundle.json" > "$pkg_tmp/import.json"
            if ! bash "$SCRIPT_DIR/lib/import-package.sh" "$pkg_tmp/import.json" > "$pkg_tmp/import-result.json"; then
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            imported_id=$(jq -r '.measure.id // empty' "$pkg_tmp/import-result.json" | tr -d '\r')
            if [ -z "$imported_id" ]; then
                echo "    ✗ import result has no .measure.id" >&2
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/approve-measure.sh" "$imported_id"; then
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            if ! bash "$SCRIPT_DIR/lib/evaluate.sh" "$imported_id" "$period_start" "$period_end" > "$pkg_tmp/imported.json"; then
                rm -rf "$pkg_tmp"; failed_scenarios+=("$name"); continue
            fi
            package_ok=1
            bash "$SCRIPT_DIR/lib/assert.sh" "$pkg_tmp/imported.json" "$expected_file" "$imported_id" || package_ok=0
            bash "$SCRIPT_DIR/lib/assert-measure-package.sh" "$pkg_tmp" "$expected_file" || package_ok=0
            if [ "$package_ok" = "1" ]; then
                passed_scenarios+=("$name")
            else
                # Keep the package next to the logs: the jq assertions are hard to debug without it.
                if [ -n "${SMOKE_LOG_DIR:-}" ]; then
                    mkdir -p "$SMOKE_LOG_DIR"
                    cp -r "$pkg_tmp" "$SMOKE_LOG_DIR/$name-package" 2>/dev/null || true
                fi
                failed_scenarios+=("$name")
            fi
            rm -rf "$pkg_tmp"
            ;;

        *)
            echo "    ✗ unknown scenario type '$scenario_type' (expected: ecqm, cds-hook, cql-execute, authoring-cql, measure-status-guard, measure-package)" >&2
            failed_scenarios+=("$name")
            ;;
    esac
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
