#!/usr/bin/env bash
# Walk a draft MeasureDefinition through the review workflow:
#   POST /{id}/submit-for-review  (draft → in-review)
#   POST /{id}/approve            (in-review → active)
# Both take a WorkflowActionRequest body ({"reason": ...}); the seeded admin is
# the creator so owner/reviewer checks pass. Fails unless the final status is
# `active` — the whole point is to lift the PAT-219 evaluation guard.
#
# Usage:  lib/approve-measure.sh <measureId>
set -euo pipefail

MEASURE_ID="${1:?usage: approve-measure.sh <measureId>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

final_status=""
for action in submit-for-review approve; do
    response=$(curl -s -X POST "$API_BASE/measures/$MEASURE_ID/$action" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -w "\n__HTTP_STATUS__%{http_code}" \
        -d '{"reason":"smoke harness PAT-219 lifecycle walk"}') || {
        echo "$action transport error (curl failed)" >&2
        exit 1
    }
    http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
    body=$(echo "$response" | sed '$d')
    if [ "$http_status" != "200" ]; then
        echo "$action failed with HTTP $http_status: $body" >&2
        exit 1
    fi
    final_status=$(echo "$body" | jq -r '.status // empty' | tr -d '\r')
    echo "  $action → status: ${final_status:-<null>}" >&2
done

if [ "$final_status" != "active" ]; then
    echo "expected measure #$MEASURE_ID to be active after approve, got '${final_status:-<null>}'" >&2
    exit 1
fi
