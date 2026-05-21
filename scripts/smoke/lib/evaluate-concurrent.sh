#!/usr/bin/env bash
# Fire N parallel $evaluate-measure calls and verify all responses are identical.
#
# Stress-tests connection pools (HikariCP, HAPI FHIR client, cqlExecutionExecutor
# thread pool) and concurrent MeasureReportService.saveReport inserts. If any
# of those misbehaves under concurrency, we'd see (a) one of the calls error,
# (b) score divergence between calls, or (c) population count drift.
#
# Usage:  lib/evaluate-concurrent.sh <measureId> <periodStart> <periodEnd> <concurrency>
# Emits:  the FIRST response on stdout (so the caller's assert.sh has something to compare)
# Exits:  0 if all N succeed and produce identical scores+populations; 1 otherwise
set -euo pipefail

MEASURE_ID="${1:?usage: evaluate-concurrent.sh <measureId> <periodStart> <periodEnd> <concurrency>}"
PERIOD_START="${2:?periodStart missing}"
PERIOD_END="${3:?periodEnd missing}"
CONCURRENCY="${4:?concurrency missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! [[ "$CONCURRENCY" =~ ^[0-9]+$ ]] || [ "$CONCURRENCY" -lt 2 ]; then
    echo "concurrency must be an integer >=2, got: $CONCURRENCY" >&2
    exit 1
fi

# Per-run output sandbox; cleaned on exit
TMPDIR=$(mktemp -d -t smoke-concurrent.XXXXXX)
trap 'rm -rf "$TMPDIR"' EXIT

echo "  firing $CONCURRENCY parallel evaluations…" >&2

# Launch N children; each writes its response to its own temp file.
# We use individual files (not stdout pipes) to keep each evaluation's response
# isolated — a pipe would interleave bytes from different processes.
pids=()
for i in $(seq 1 "$CONCURRENCY"); do
    out="$TMPDIR/run-${i}.json"
    err="$TMPDIR/run-${i}.err"
    bash "$SCRIPT_DIR/evaluate.sh" "$MEASURE_ID" "$PERIOD_START" "$PERIOD_END" >"$out" 2>"$err" &
    pids+=("$!:$i")
done

# Wait for each child; fail fast if any errored
fail=0
for pid_pair in "${pids[@]}"; do
    pid="${pid_pair%%:*}"
    idx="${pid_pair##*:}"
    if ! wait "$pid"; then
        echo "    ✗ concurrent run #$idx failed:" >&2
        head -3 "$TMPDIR/run-${idx}.err" >&2 || true
        fail=1
    fi
done
if [ "$fail" -eq 1 ]; then
    exit 1
fi

# Compare all runs to run-1: same score and same per-population counts.
# Tolerance is 0 — concurrent runs against the same FHIR snapshot MUST produce
# identical numbers. Any drift is a real concurrency bug, not floating-point noise.
ref_score=$(jq -r '.groups[0].measureScore // "null"' "$TMPDIR/run-1.json" | tr -d '\r')
ref_pops=$(jq -S '.groups[0].populations | map({populationType, count})' "$TMPDIR/run-1.json")

for i in $(seq 2 "$CONCURRENCY"); do
    cur_score=$(jq -r '.groups[0].measureScore // "null"' "$TMPDIR/run-${i}.json" | tr -d '\r')
    cur_pops=$(jq -S '.groups[0].populations | map({populationType, count})' "$TMPDIR/run-${i}.json")
    if [ "$ref_score" != "$cur_score" ]; then
        echo "    ✗ concurrent score divergence: run-1=$ref_score run-${i}=$cur_score" >&2
        fail=1
    fi
    if [ "$ref_pops" != "$cur_pops" ]; then
        echo "    ✗ concurrent population divergence between run-1 and run-${i}:" >&2
        diff <(echo "$ref_pops") <(echo "$cur_pops") >&2 || true
        fail=1
    fi
done

if [ "$fail" -eq 0 ]; then
    echo "    ✓ $CONCURRENCY concurrent evaluations identical (score=$ref_score)" >&2
fi

# Echo run-1 to stdout so the caller's assert.sh can still apply its standard checks
cat "$TMPDIR/run-1.json"
exit "$fail"
