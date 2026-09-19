#!/usr/bin/env bash
# Call the measure test-case API WITHOUT treating a non-2xx as a transport failure.
# Emits `HTTP_STATUS\n---HTTP_STATUS_BODY---\nBODY` (same envelope as evaluate-raw.sh /
# update-measure-raw.sh) so an assert step can branch on the status — PAT-228 scenarios
# expect both successes (create / run) and a refusal (an expectation naming a group the
# measure does not have → 400).
#
# Usage:
#   lib/test-case-raw.sh POST <measureId> ""               body.json   # create
#   lib/test-case-raw.sh PUT  <measureId> "/<tcId>"        body.json   # update
#   lib/test-case-raw.sh POST <measureId> "/<tcId>/run"                # run
set -euo pipefail

METHOD="${1:?usage: test-case-raw.sh <METHOD> <measureId> <pathSuffix> [body.json]}"
MEASURE_ID="${2:?measureId missing}"
SUFFIX="${3-}"
BODY_FILE="${4-}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

args=(-s -X "$METHOD" "$API_BASE/measures/$MEASURE_ID/test-cases$SUFFIX"
      -H "Content-Type: application/json"
      -H "Authorization: Bearer $TOKEN"
      -w "\n__HTTP_STATUS__%{http_code}")
if [ -n "$BODY_FILE" ]; then
    if [ ! -f "$BODY_FILE" ]; then
        echo "body file not found: $BODY_FILE" >&2
        exit 1
    fi
    args+=(--data-binary "@$BODY_FILE")
fi

response=$(curl "${args[@]}") || {
    echo "test-case-raw transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')

printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$http_status" "$body"
