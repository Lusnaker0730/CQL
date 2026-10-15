#!/usr/bin/env bash
# PAT-229 — POST a FHIR package to /api/measures/import/bundle. Writes the
# BundleImportResult JSON ({measure, librariesImported, librariesSkipped,
# valueSetsFound}) to stdout.
#
# PAT-261: a .zip is sent to POST /api/measures/import/package (multipart) instead; the
# optional second argument is passed as ?asVersion= (replaces Measure.version on the server).
#
# Usage:  lib/import-package.sh <bundle.json | package.zip> [asVersion]
set -euo pipefail

BUNDLE="${1:?usage: import-package.sh <bundle.json | package.zip> [asVersion]}"
AS_VERSION="${2:-}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

[ -f "$BUNDLE" ] || { echo "bundle file not found: $BUNDLE" >&2; exit 1; }

query=""
[ -n "$AS_VERSION" ] && query="?asVersion=$AS_VERSION"
case "$BUNDLE" in
    *.zip)
        # Windows curl cannot open an MSYS absolute path behind -F "file=@...": cd next to the file.
        response=$(cd "$(dirname "$BUNDLE")" && curl -s -X POST "$API_BASE/measures/import/package$query" \
            -H "Authorization: Bearer $TOKEN" \
            -w "\n__HTTP_STATUS__%{http_code}" \
            -F "file=@$(basename "$BUNDLE");type=application/zip") || {
            echo "import-package transport error (curl failed)" >&2
            exit 1
        }
        ;;
    *)
        response=$(curl -s -X POST "$API_BASE/measures/import/bundle$query" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $TOKEN" \
            -w "\n__HTTP_STATUS__%{http_code}" \
            --data-binary "@$BUNDLE") || {
            echo "import-package transport error (curl failed)" >&2
            exit 1
        }
        ;;
esac
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "import-package failed with HTTP $http_status: $body" >&2
    exit 1
fi
echo "  imported as measure #$(echo "$body" | jq -r '.measure.id // "?"' | tr -d '\r')" >&2

echo "$body"
