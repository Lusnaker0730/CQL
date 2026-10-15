#!/usr/bin/env bash
# PAT-229 — download everything a measure author hands to another organisation:
#   <outDir>/bundle.json        GET /api/measures/{id}/export/bundle?format=json
#   <outDir>/bundle.xml         GET /api/measures/{id}/export/bundle?format=xml
#   <outDir>/conformance.json   GET /api/measures/{id}/export/conformance
#   <outDir>/package.zip        GET /api/measures/{id}/export/package      (PAT-261, unzipped into <outDir>/zip/)
#   <outDir>/elm.xml            GET /api/measures/{id}/export/elm?format=xml
#
# Usage:  lib/export-package.sh <measureId> <outDir>
set -euo pipefail

MEASURE_ID="${1:?usage: export-package.sh <measureId> <outDir>}"
OUT_DIR="${2:?outDir missing}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

fetch() {
    local path="$1" target="$2" status
    status=$(curl -s -o "$target" -D "$target.headers" -w "%{http_code}" -X GET "$API_BASE/measures/$MEASURE_ID/$path" \
        -H "Authorization: Bearer $TOKEN") || {
        echo "export $path transport error (curl failed)" >&2
        exit 1
    }
    if [ "$status" != "200" ]; then
        echo "export $path failed with HTTP $status: $(head -c 2000 "$target")" >&2
        exit 1
    fi
}

fetch "export/bundle?format=json" "$OUT_DIR/bundle.json"
fetch "export/bundle?format=xml" "$OUT_DIR/bundle.xml"
fetch "export/conformance" "$OUT_DIR/conformance.json"
fetch "export/package" "$OUT_DIR/package.zip"
fetch "export/elm?format=xml" "$OUT_DIR/elm.xml"
mkdir -p "$OUT_DIR/zip"
if ! unzip -q -o "$OUT_DIR/package.zip" -d "$OUT_DIR/zip"; then
    echo "export/package is not a readable zip" >&2
    exit 1
fi
echo "  exported package of measure #$MEASURE_ID ($(jq '.entry | length' "$OUT_DIR/bundle.json" | tr -d '\r') entries)" >&2
