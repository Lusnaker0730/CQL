#!/usr/bin/env bash
# PAT-255 synthetic cohort: the platform's own TW Core patient generator produced bundle.json (120
# patients, fixed seed — see frontend/src/utils/syntheticCohort.ts); expected.json carries the oracle
# computed in TypeScript over those resources. Seeds the FHIR server with the cohort, evaluates the
# seeded demo measure `DiabetesHbA1cRate` for the cohort's period and asserts the exact initial
# population / denominator / numerator and score, plus the evaluation-time budget — the first
# scenario whose data was not hand-written around the measure.
#
# Usage: lib/run-synthetic-cohort.sh <scenarioDir> <expected.json>
set -euo pipefail

SCENARIO_DIR="${1:?usage: run-synthetic-cohort.sh <scenarioDir> <expected.json>}"
EXPECTED="${2:?expected.json missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

bundle_file="$SCENARIO_DIR/bundle.json"
[ -f "$bundle_file" ] || { echo "    ✗ missing $bundle_file (run npm run gen:cohort in frontend/)" >&2; exit 1; }
measure_name=$(jq -r '.measureName // "DiabetesHbA1cRate"' "$EXPECTED" | tr -d '\r')
period_start=$(jq -r '.period.start' "$EXPECTED" | tr -d '\r')
period_end=$(jq -r '.period.end' "$EXPECTED" | tr -d '\r')
patients=$(jq -r '.patientCount' "$EXPECTED" | tr -d '\r')
resources=$(jq -r '.entry | length' "$bundle_file" | tr -d '\r')
echo "  cohort: $patients patients / $resources resources, period $period_start..$period_end" >&2

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# 1. seed the cohort (one transaction; seed-fhir.sh retries transient HAPI 5xx)
bash "$SCRIPT_DIR/seed-fhir.sh" "$bundle_file"
fhir_patients=$(curl -sf "${FHIR_BASE:-http://localhost:8081/fhir}/Patient?_summary=count" | jq -r '.total' | tr -d '\r')
echo "  FHIR now holds $fhir_patients patients" >&2

# 2. the seeded demo measure (DataInitializer; active on a fresh database)
measure_id=$(curl -sf "$API_BASE/measures?search=$measure_name" -H "Authorization: Bearer $TOKEN" \
    | jq -r --arg n "$measure_name" '[.[] | select(.name == $n)][0].id // empty' | tr -d '\r')
[ -n "$measure_id" ] || { echo "    ✗ measure '$measure_name' not found" >&2; exit 1; }
echo "  evaluating measure #$measure_id ($measure_name)" >&2

# 3. evaluate for the cohort's period and time it (assert.sh checks maxEvaluationTimeMs)
eval_start_ns=$(date +%s%N)
bash "$SCRIPT_DIR/evaluate.sh" "$measure_id" "$period_start" "$period_end" > "$tmp/response.json"
eval_end_ns=$(date +%s%N)
EVAL_ELAPSED_MS=$(( (eval_end_ns - eval_start_ns) / 1000000 ))
export EVAL_ELAPSED_MS

errors=$(jq -r '(.errors // []) | length' "$tmp/response.json" | tr -d '\r')
if [ "$errors" != "0" ]; then
    echo "    ✗ evaluation reported $errors error(s): $(jq -c '.errors' "$tmp/response.json" | head -c 300)" >&2
    exit 1
fi
echo "    ✓ evaluation ran without errors (${EVAL_ELAPSED_MS}ms)" >&2

# 4. exact populations + score from the oracle
bash "$SCRIPT_DIR/assert.sh" "$tmp/response.json" "$EXPECTED" "$measure_id"
