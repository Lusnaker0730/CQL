#!/usr/bin/env bash
# PUT /api/measures/{id} with the given body WITHOUT treating a non-2xx as a
# transport failure. Emits `HTTP_STATUS\n---HTTP_STATUS_BODY---\nBODY` (same
# envelope as evaluate-raw.sh) so an assert step can branch on status — used by
# scenarios that EXPECT a refusal (PAT-222: a status change via PUT → 400).
#
# Usage:  lib/update-measure-raw.sh <measureId> <body.json>
set -euo pipefail

MEASURE_ID="${1:?usage: update-measure-raw.sh <measureId> <body.json>}"
BODY_FILE="${2:?body.json missing}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$BODY_FILE" ]; then
    echo "body file not found: $BODY_FILE" >&2
    exit 1
fi

response=$(curl -s -X PUT "$API_BASE/measures/$MEASURE_ID" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$BODY_FILE") || {
    echo "update-measure-raw transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')

printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$http_status" "$body"
