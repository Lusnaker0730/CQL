#!/usr/bin/env bash
# Save an eCQM artifact and publish it. Emits the published measure ID (the one
# /api/measures/{id}/$evaluate-measure takes) to stdout. Designed to be called
# as:  MEASURE_ID=$(lib/save-and-publish.sh measure.json)
#
# Requires TOKEN env var set to a valid JWT.
set -euo pipefail

MEASURE="${1:?usage: save-and-publish.sh <measure.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$MEASURE" ]; then
    echo "measure file not found: $MEASURE" >&2
    exit 1
fi

# Save — curl without -f so we see error bodies (validation errors etc) on stderr.
# The save endpoint returns 4xx with a JSON error payload we want to surface.
save_response=$(curl -s -X POST "$API_BASE/ecqm/artifacts" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$MEASURE") || {
    echo "save curl failed: $save_response" >&2
    exit 1
}
http_status=$(echo "$save_response" | tail -1 | sed 's/__HTTP_STATUS__//')
save_response=$(echo "$save_response" | sed '$d')
if [ "$http_status" != "200" ] && [ "$http_status" != "201" ]; then
    echo "save failed with HTTP $http_status: $save_response" >&2
    exit 1
fi

artifact_id=$(echo "$save_response" | jq -r '.id // empty')
if [ -z "$artifact_id" ]; then
    echo "save response missing .id: $save_response" >&2
    exit 1
fi
echo "  saved artifact #$artifact_id" >&2

# Publish → creates MeasureDefinition, returns its ID
publish_response=$(curl -sf -X POST "$API_BASE/ecqm/artifacts/$artifact_id/publish" \
    -H "Authorization: Bearer $TOKEN" 2>&1) || {
    echo "publish failed: $publish_response" >&2
    exit 1
}

measure_id=$(echo "$publish_response" | jq -r '.measureDefinitionId // .publishedMeasureId // .id // empty')
if [ -z "$measure_id" ]; then
    echo "publish response missing measureDefinitionId/publishedMeasureId/id: $publish_response" >&2
    exit 1
fi
echo "  published as measure #$measure_id" >&2

printf '%s' "$measure_id"
