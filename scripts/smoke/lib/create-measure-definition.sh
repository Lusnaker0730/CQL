#!/usr/bin/env bash
# Create a raw MeasureDefinition via POST /api/measures and emit its numeric id.
#
# This is the *editor* path (a hand-written measure with cqlContent), NOT the
# eCQM artifact path used by save-and-publish.sh — publish always lands the
# definition as `active`, so it can never exercise the draft lifecycle. A raw
# definition defaults to `draft` (PAT-219 status-guard scenarios need that).
#
# Usage:  MEASURE_ID=$(lib/create-measure-definition.sh measure.json)
# Requires TOKEN env var set to a valid JWT.
set -euo pipefail

MEASURE="${1:?usage: create-measure-definition.sh <measure.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$MEASURE" ]; then
    echo "measure file not found: $MEASURE" >&2
    exit 1
fi

# No -f: a 4xx validation payload is more useful on stderr than a bare curl error.
response=$(curl -s -X POST "$API_BASE/measures" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$MEASURE") || {
    echo "create-measure-definition transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ] && [ "$http_status" != "201" ]; then
    echo "create failed with HTTP $http_status: $body" >&2
    exit 1
fi

measure_id=$(echo "$body" | jq -r '.id // empty')
if [ -z "$measure_id" ]; then
    echo "create response missing .id: $body" >&2
    exit 1
fi
status=$(echo "$body" | jq -r '.status // empty')
echo "  created measure definition #$measure_id (status: ${status:-<null>})" >&2

printf '%s' "$measure_id"
