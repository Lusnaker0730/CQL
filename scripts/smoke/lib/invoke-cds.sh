#!/usr/bin/env bash
# Invoke a CDS hook service via POST /cds-services/{serviceId}.
#
# Since BUG-139 there is no anonymous invocation. The route is still permitAll at
# the Spring Security layer (CDS Hooks spec — EHRs call it from their own auth
# context), but CdsHooksService.invokeService authorizes the CALLER: a shared
# service is invocable by anyone in its own tenant, a private one only by its
# owner (BUG-142), and an unauthenticated caller gets the same "not available"
# info card as a missing service so ids aren't probeable. The seeded admin owns
# every service the harness saves, so we send its JWT — without it every CDS
# scenario "passes" the HTTP call and fails the card assertions (first CI run
# of PAT-220 hit exactly that: `CDS invoke denied ... (caller=null)`).
#
# A `disabled` service also answers 200 + the same info card (scenario 14).
#
# Writes the full CdsResponse JSON to stdout.
#
# Usage:  lib/invoke-cds.sh <serviceId> <invocation.json>
set -euo pipefail

SERVICE_ID="${1:?usage: invoke-cds.sh <serviceId> <invocation.json>}"
INVOCATION="${2:?invocation.json missing}"
CDS_BASE="${CDS_BASE:-http://localhost:8080/cds-services}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$INVOCATION" ]; then
    echo "invocation file not found: $INVOCATION" >&2
    exit 1
fi

response=$(curl -s -X POST "$CDS_BASE/$SERVICE_ID" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$INVOCATION")
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "invoke-cds failed with HTTP $http_status: $body" >&2
    exit 1
fi

echo "$body"
