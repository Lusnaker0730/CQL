#!/usr/bin/env bash
# Walk a draft MeasureDefinition through the review workflow:
#   POST /{id}/submit-for-review  as the caller (the seeded admin, who created it)   draft → in-review
#   POST /{id}/approve            as the caller must be REFUSED (PAT-249 four-eyes: 403 Approval Not Allowed)
#   POST /{id}/share              with the reviewer (seeded `demo`)
#   POST /{id}/approve            as the reviewer                                     in-review → active
# Fails unless the final status is `active` — the whole point is to lift the PAT-219
# evaluation guard. Every ecqm scenario goes through here, so the four-eyes refusal is
# exercised ~30 times per run; the PAT-249 readiness gate lets these measures through
# because they have no test cases yet (NO_TEST_CASES is a warning, not a blocker).
# SMOKE_FOUR_EYES=0 skips the refusal assertion for stacks that run with
# MEASURE_REVIEW_FOUR_EYES=false.
#
# Usage:  lib/approve-measure.sh <measureId>
set -euo pipefail

MEASURE_ID="${1:?usage: approve-measure.sh <measureId>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REVIEWER="${SMOKE_REVIEWER:-demo}"
REVIEWER_PASSWORD="${SMOKE_REVIEWER_PASSWORD:-password}"

# post <path> <token> <body> → sets http_status / body
post() {
    local response
    response=$(curl -s -X POST "$API_BASE/measures/$MEASURE_ID/$1" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $2" \
        -w "\n__HTTP_STATUS__%{http_code}" \
        -d "$3") || {
        echo "$1 transport error (curl failed)" >&2
        exit 1
    }
    http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
    body=$(echo "$response" | sed '$d')
}

post submit-for-review "$TOKEN" '{"reason":"smoke harness PAT-219 lifecycle walk"}'
if [ "$http_status" != "200" ]; then
    echo "submit-for-review failed with HTTP $http_status: $body" >&2
    exit 1
fi
echo "  submit-for-review → status: $(echo "$body" | jq -r '.status // empty' | tr -d '\r')" >&2

if [ "${SMOKE_FOUR_EYES:-1}" != "0" ]; then
    post approve "$TOKEN" '{"reason":"smoke harness self-approval (must be refused)"}'
    if [ "$http_status" != "403" ] || [ "$(echo "$body" | jq -r '.error // empty')" != "Approval Not Allowed" ]; then
        echo "expected the submitter's own approve to be refused with 403 'Approval Not Allowed' (PAT-249 four-eyes), got HTTP $http_status: $body" >&2
        exit 1
    fi
    echo "  approve as submitter → 403 Approval Not Allowed (four-eyes)" >&2
fi

post share "$TOKEN" "$(jq -nc --arg u "$REVIEWER" '{targetUsername: $u}')"
if [ "$http_status" != "200" ]; then
    echo "share with $REVIEWER failed with HTTP $http_status: $body" >&2
    exit 1
fi

reviewer_token=$(SMOKE_USER="$REVIEWER" SMOKE_PASSWORD="$REVIEWER_PASSWORD" bash "$SCRIPT_DIR/auth.sh")
post approve "$reviewer_token" '{"reason":"smoke harness PAT-219 lifecycle walk (reviewer)"}'
if [ "$http_status" != "200" ]; then
    echo "approve as $REVIEWER failed with HTTP $http_status: $body" >&2
    exit 1
fi
final_status=$(echo "$body" | jq -r '.status // empty' | tr -d '\r')
echo "  approve as $REVIEWER → status: ${final_status:-<null>}" >&2

if [ "$final_status" != "active" ]; then
    echo "expected measure #$MEASURE_ID to be active after approve, got '${final_status:-<null>}'" >&2
    exit 1
fi
