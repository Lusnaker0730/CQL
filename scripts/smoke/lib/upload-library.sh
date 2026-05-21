#!/usr/bin/env bash
# Upload a CQL library file to /api/cql/libraries so the engine's
# DatabaseLibrarySourceProvider can resolve `include` statements at evaluate time.
#
# Used by scenario 22-external-cql-library (and any future scenario that needs
# pre-existing library state) — invoked from run.sh when `expected.json.uploadLibrary`
# names a CQL file. Library name + version are parsed from the file's `library X
# version 'Y.Y.Y'` header by the backend.
#
# Usage:  lib/upload-library.sh <library.cql>
set -euo pipefail

CQL_FILE="${1:?usage: upload-library.sh <library.cql>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$CQL_FILE" ]; then
    echo "library file not found: $CQL_FILE" >&2
    exit 1
fi

# Wrap CQL content into the LibrarySaveRequest JSON shape — `cql` field is the
# whole library text. jq does the JSON encoding (handles newlines, quotes, etc.).
payload=$(jq -n --arg cql "$(cat "$CQL_FILE")" --arg desc "Smoke test external library" \
    '{cql: $cql, description: $desc}')

response=$(curl -s -X POST "$API_BASE/cql/libraries" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "$payload") || {
    echo "upload-library curl failed: $response" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')

# Accept 200/201 = saved; 409 = already exists from a prior run that didn't tear
# down (e.g. --keep). Treat 409 as success — engine resolves by name+version, the
# existing row is fine.
if [ "$http_status" = "201" ] || [ "$http_status" = "200" ]; then
    lib_id=$(echo "$body" | jq -r '.id // .name // "?"')
    echo "  uploaded library #$lib_id" >&2
elif [ "$http_status" = "409" ]; then
    echo "  library already exists (HTTP 409) — OK, reusing" >&2
else
    echo "upload-library failed with HTTP $http_status: $body" >&2
    exit 1
fi
