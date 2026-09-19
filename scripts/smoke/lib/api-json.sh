#!/usr/bin/env bash
# Generic authenticated JSON call against the backend API. Prints the response
# body on stdout; any non-2xx status is a failure (body goes to stderr).
#
# Usage:  lib/api-json.sh <METHOD> <path-under-/api> [body.json]
#   e.g.  lib/api-json.sh POST /value-sets body.json
#         lib/api-json.sh POST /value-sets/3/activate
set -euo pipefail

METHOD="${1:?usage: api-json.sh <METHOD> <path> [body.json]}"
API_PATH="${2:?path missing}"
BODY_FILE="${3:-}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

args=(-s -X "$METHOD" "$API_BASE$API_PATH" -H "Authorization: Bearer $TOKEN" -w "\n__HTTP_STATUS__%{http_code}")
if [ -n "$BODY_FILE" ]; then
    [ -f "$BODY_FILE" ] || { echo "body file not found: $BODY_FILE" >&2; exit 1; }
    args+=(-H "Content-Type: application/json" --data-binary "@$BODY_FILE")
fi

response=$(curl "${args[@]}") || {
    echo "$METHOD $API_PATH transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
case "$http_status" in
    2*) echo "$body" ;;
    *)  echo "$METHOD $API_PATH failed with HTTP $http_status: $body" >&2; exit 1 ;;
esac
