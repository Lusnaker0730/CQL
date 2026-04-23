#!/usr/bin/env bash
# POST an artifact JSON body to /api/authoring/artifacts, then call
# POST /api/authoring/artifacts/{id}/cql to get the generated CQL.
# Writes the response body of the /cql call to stdout so the caller can
# assert on it.
#
# Flow exercised (same one the CDS LibraryDefinitionPicker triggers):
#   Jackson deserialize → ArtifactRequest → ArtifactService.create →
#   CdsArtifactEntity @PrePersist → save → @PostLoad →
#   CqlArtifactBuilder.buildCql → FreeMarker → CQL string
#
# Usage:  lib/generate-authoring-cql.sh <artifact.json>
set -euo pipefail

ARTIFACT="${1:?usage: generate-authoring-cql.sh <artifact.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$ARTIFACT" ]; then
    echo "artifact file not found: $ARTIFACT" >&2
    exit 1
fi

# Save — artifact endpoint returns 201 with body containing the new .id
save_response=$(curl -s -X POST "$API_BASE/authoring/artifacts" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$ARTIFACT") || {
    echo "save curl failed: $save_response" >&2
    exit 1
}
http_status=$(echo "$save_response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$save_response" | sed '$d')
if [ "$http_status" != "200" ] && [ "$http_status" != "201" ]; then
    echo "artifact save failed with HTTP $http_status: $body" >&2
    exit 1
fi

artifact_id=$(echo "$body" | jq -r '.id // empty')
if [ -z "$artifact_id" ]; then
    echo "save response missing .id: $body" >&2
    exit 1
fi
echo "  saved artifact #$artifact_id" >&2

# Generate CQL — endpoint returns 200 with { "cql": "...", "warnings": [...] }
cql_response=$(curl -sf -X POST "$API_BASE/authoring/artifacts/$artifact_id/cql" \
    -H "Authorization: Bearer $TOKEN" 2>&1) || {
    echo "generate CQL failed: $cql_response" >&2
    exit 1
}
echo "$cql_response"
