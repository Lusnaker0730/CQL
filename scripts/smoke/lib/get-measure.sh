#!/usr/bin/env bash
# GET /api/measures/{id} and write the MeasureDefinition JSON to stdout.
# Used by scenarios that assert server-stamped fields (e.g. PAT-222's
# ownerUsername) rather than what the client sent.
#
# Usage:  lib/get-measure.sh <measureId>
set -euo pipefail

MEASURE_ID="${1:?usage: get-measure.sh <measureId>}"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"

response=$(curl -sf -X GET "$API_BASE/measures/$MEASURE_ID" \
    -H "Authorization: Bearer $TOKEN" 2>&1) || {
    echo "get-measure failed: $response" >&2
    exit 1
}

echo "$response"
