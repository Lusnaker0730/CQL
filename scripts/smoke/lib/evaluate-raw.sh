#!/usr/bin/env bash
# Run $evaluate-measure WITHOUT treating a non-2xx as a transport failure.
# Emits `HTTP_STATUS\n---HTTP_STATUS_BODY---\nBODY` (same envelope as
# execute-cql.sh) so an assert step can branch on status — needed for
# scenarios that EXPECT a 4xx (e.g. PAT-219: draft measure → 409).
#
# evaluate.sh stays the happy-path helper (curl -sf, fails on any 4xx/5xx);
# use this one only when the HTTP status itself is the assertion target.
#
# Usage:  lib/evaluate-raw.sh <measureId> <periodStart> <periodEnd>
set -euo pipefail

MEASURE_ID="${1:?usage: evaluate-raw.sh <measureId> <periodStart> <periodEnd>}"
PERIOD_START="${2:?periodStart missing}"
PERIOD_END="${3:?periodEnd missing}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

response=$(curl -s -X POST \
    "$API_BASE/measures/$MEASURE_ID/\$evaluate-measure?periodStart=$PERIOD_START&periodEnd=$PERIOD_END&reportType=summary" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}") || {
    echo "evaluate-raw transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')

printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$http_status" "$body"
