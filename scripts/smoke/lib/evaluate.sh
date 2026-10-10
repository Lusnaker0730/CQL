#!/usr/bin/env bash
# Run $evaluate-measure against a published measure. Writes full response JSON
# to stdout for the caller's assert step to parse.
#
# Usage:  lib/evaluate.sh <measureId> [periodStart] [periodEnd]   e.g.  7 2020-01-01 2020-06-30
# PAT-242: with the period arguments empty the request names no period, so the backend falls
# back to the measure's own Measurement Period (then the configured default / current year).
set -euo pipefail

MEASURE_ID="${1:?usage: evaluate.sh <measureId> [periodStart] [periodEnd]}"
PERIOD_START="${2-}"
PERIOD_END="${3-}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

query="reportType=summary"
[ -n "$PERIOD_START" ] && query="$query&periodStart=$PERIOD_START"
[ -n "$PERIOD_END" ] && query="$query&periodEnd=$PERIOD_END"

response=$(curl -sf -X POST \
    "$API_BASE/measures/$MEASURE_ID/\$evaluate-measure?$query" \
    -H "Authorization: Bearer $TOKEN" 2>&1) || {
    echo "evaluate failed: $response" >&2
    exit 1
}

# MeasureReport status from HAPI is one of: complete | pending | error
# (FHIR R4 spec). Treat only "error" as a failure; "complete" / "pending"
# both flow through — the assert step will catch missing fields if any.
status=$(echo "$response" | jq -r '.status // empty')
if [ "$status" = "error" ]; then
    echo "evaluate failed: $(echo "$response" | jq -r '.errorMessage // "no errorMessage"')" >&2
    echo "$response" | jq . >&2
    exit 1
fi

echo "$response"
