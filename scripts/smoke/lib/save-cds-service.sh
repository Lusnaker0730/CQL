#!/usr/bin/env bash
# Create a CDS Hook service via POST /api/cds/services. Unlike eCQM, there's no
# separate publish step — saving an enabled service makes it immediately
# discoverable at GET /cds-services and invocable at POST /cds-services/{id}.
#
# Emits the service ID to stdout so callers can pipe it into invoke-cds.sh.
# Service ID is declared inside service.json (required NotBlank field).
#
# Usage:  SERVICE_ID=$(lib/save-cds-service.sh service.json)
set -euo pipefail

SERVICE="${1:?usage: save-cds-service.sh <service.json>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

if [ ! -f "$SERVICE" ]; then
    echo "service file not found: $SERVICE" >&2
    exit 1
fi

# curl without -f so we surface 4xx validation bodies on stderr (same pattern as
# save-and-publish.sh after the PAT-087 fix — silent -f swallowed errors ate hours).
response=$(curl -s -X POST "$API_BASE/cds/services" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    --data-binary "@$SERVICE")
http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
body=$(echo "$response" | sed '$d')
if [ "$http_status" != "200" ] && [ "$http_status" != "201" ]; then
    echo "save CDS service failed with HTTP $http_status: $body" >&2
    exit 1
fi

service_id=$(echo "$body" | jq -r '.id // empty')
if [ -z "$service_id" ]; then
    echo "save response missing .id: $body" >&2
    exit 1
fi
echo "  saved CDS service $service_id" >&2

printf '%s' "$service_id"
