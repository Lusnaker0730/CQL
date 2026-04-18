#!/usr/bin/env bash
# POST a FHIR transaction Bundle (or individual resources) to HAPI FHIR.
# The bundle must be a `transaction` Bundle with `fullUrl` + `request.method: PUT`
# entries — PUT preserves client-side IDs so fixtures can reference stable IDs.
#
# Usage:  lib/seed-fhir.sh path/to/bundle.json
#
# Exits nonzero on HAPI transaction failure (any entry status >= 400 makes the
# whole transaction roll back in FHIR semantics — we surface that here).
set -euo pipefail

BUNDLE="${1:?usage: seed-fhir.sh <bundle.json>}"
FHIR_BASE="${FHIR_BASE:-http://localhost:8081/fhir}"

if [ ! -f "$BUNDLE" ]; then
    echo "bundle file not found: $BUNDLE" >&2
    exit 1
fi

response=$(curl -sf -X POST "$FHIR_BASE" \
    -H "Content-Type: application/fhir+json" \
    --data-binary "@$BUNDLE" 2>&1) || {
    echo "FHIR transaction POST failed: $response" >&2
    exit 1
}

# HAPI returns a response Bundle with one entry per request; check for any error statuses.
failed=$(echo "$response" | jq -r '
    .entry[]?.response.status
    | select(test("^[45]"))' 2>/dev/null || true)

if [ -n "$failed" ]; then
    echo "FHIR transaction had failed entries: $failed" >&2
    echo "$response" | jq . >&2
    exit 1
fi

entry_count=$(echo "$response" | jq '.entry | length' 2>/dev/null || echo 0)
echo "  seeded $entry_count FHIR resources"
