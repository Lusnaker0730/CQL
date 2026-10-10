#!/usr/bin/env bash
# PAT-229 — POST a FHIR package to /api/measures/import/bundle. Writes the
# BundleImportResult JSON ({measure, librariesImported, librariesSkipped,
# valueSetsFound}) to stdout.
#
# Usage:  lib/import-package.sh <bundle.json>
set -euo pipefail

BUNDLE="${1:?usage: import-package.sh <bundle.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

[ -f "$BUNDLE" ] || { echo "bundle file not found: $BUNDLE" >&2; exit 1; }

response=$(curl -s -X POST "$API_BASE/measures/import/bundle" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$BUNDLE") || {
    echo "import-package transport error (curl failed)" >&2
    exit 1
}
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "import-package failed with HTTP $http_status: $body" >&2
    exit 1
fi
echo "  imported as measure #$(echo "$body" | jq -r '.measure.id // "?"' | tr -d '\r')" >&2

echo "$body"
