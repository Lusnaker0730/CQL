#!/usr/bin/env bash
# POST a CQL body to /api/cql/execute. Writes the full CqlExecutionResponse
# JSON to stdout for the caller's assert step. Used by cql-execute scenarios
# (debug-mode smoke coverage) — NOT by eCQM/CDS scenarios which go through
# dedicated pipelines.
#
# Usage:  lib/execute-cql.sh <request.json>
set -euo pipefail

REQUEST="${1:?usage: execute-cql.sh <request.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$REQUEST" ]; then
    echo "request file not found: $REQUEST" >&2
    exit 1
fi

response=$(curl -s -X POST "$API_BASE/cql/execute" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$REQUEST")
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "execute-cql failed with HTTP $http_status: $body" >&2
    exit 1
fi

echo "$body"
