#!/usr/bin/env bash
# Invoke a CDS hook service via POST /cds-services/{serviceId}.
#
# The invocation endpoint is intentionally unauthenticated in the CDS Hooks spec
# (EHRs call it from their own auth context) — we don't pass a TOKEN here. If
# the service is `disabled`, HAPI returns 404 which this script treats as an
# error; scenarios that WANT to assert 404 should use invoke-cds-expect.sh.
#
# Writes the full CdsResponse JSON to stdout.
#
# Usage:  lib/invoke-cds.sh <serviceId> <invocation.json>
set -euo pipefail

SERVICE_ID="${1:?usage: invoke-cds.sh <serviceId> <invocation.json>}"
INVOCATION="${2:?invocation.json missing}"
CDS_BASE="${CDS_BASE:-http://localhost:8080/cds-services}"

if [ ! -f "$INVOCATION" ]; then
    echo "invocation file not found: $INVOCATION" >&2
    exit 1
fi

response=$(curl -s -X POST "$CDS_BASE/$SERVICE_ID" \
    -H "Content-Type: application/json" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$INVOCATION")
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "invoke-cds failed with HTTP $http_status: $body" >&2
    exit 1
fi

echo "$body"
