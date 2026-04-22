#!/usr/bin/env bash
# POST a CQL body to /api/cql/execute. Writes `HTTP_STATUS|BODY` to stdout so
# the caller's assert step can branch on status without a second parser pass.
# Used by cql-execute scenarios (debug-mode + error-path smoke coverage) —
# NOT by eCQM/CDS scenarios which go through dedicated pipelines.
#
# Exit behaviour:
#   - Network / auth / transport errors: exit 1 (can't call API)
#   - HTTP 2xx/4xx/5xx application responses: exit 0 (caller decides)
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
    --data-binary "@$REQUEST") || {
    echo "execute-cql transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')

# Emit as two segments separated by the line `---HTTP_STATUS_BODY---`, which can't
# appear inside a JSON response. Simpler parsers can take `head -n1` for status.
printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$http_status" "$body"
