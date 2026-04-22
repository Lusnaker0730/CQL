#!/usr/bin/env bash
# Compare an evaluation response against an expected.json fixture.
#
# expected.json shape:
#   {
#     "score": 0.6,              # optional — compared with 1e-6 tolerance if numeric
#     "scoreTolerance": 0.001,   # optional — override default tolerance
#     "populations": {
#       "initial-population": 7,
#       "denominator": 5,
#       "numerator": 3
#     },
#     "checkProvenance": true    # optional — follow-up GET /api/measures/{id}/reports
#                                # to verify PAT-095 provenance fields (measureVersion,
#                                # cqlHash, elmHash) are non-null on the saved report.
#                                # Requires measureId as the optional 3rd arg.
#   }
#
# Usage:  lib/assert.sh <response.json-or-stdin> <expected.json> [measureId]
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert.sh <response.json|-> <expected.json> [measureId]}"
EXPECTED="${2:?expected.json path missing}"
MEASURE_ID="${3:-}"

if [ "$RESPONSE_INPUT" = "-" ]; then
    RESPONSE=$(cat)
else
    RESPONSE=$(cat "$RESPONSE_INPUT")
fi

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

fail=0

# Score
expected_score=$(jq -r '.score // empty' "$EXPECTED")
if [ -n "$expected_score" ]; then
    tolerance=$(jq -r '.scoreTolerance // 0.001' "$EXPECTED")
    actual_score=$(echo "$RESPONSE" | jq -r '.groups[0].measureScore // empty')
    if [ -z "$actual_score" ]; then
        echo "    ✗ score: expected $expected_score, got null" >&2
        fail=1
    else
        diff=$(awk -v a="$actual_score" -v b="$expected_score" 'BEGIN{d=a-b; if (d<0) d=-d; print d}')
        within=$(awk -v d="$diff" -v t="$tolerance" 'BEGIN{print (d<=t)?1:0}')
        if [ "$within" = "1" ]; then
            echo "    ✓ score: $actual_score (expected $expected_score, Δ=$diff)"
        else
            echo "    ✗ score: $actual_score (expected $expected_score, Δ=$diff > tolerance $tolerance)" >&2
            fail=1
        fi
    fi
fi

# Populations (keyed by population-type).
# Use `tr -d '\r'` to strip Windows CRLF line endings from jq output — without it,
# pop_key becomes "denominator\r" and the lookup quietly returns null on both
# sides, producing false-positive passes.
while IFS= read -r pop_key; do
    [ -z "$pop_key" ] && continue
    expected_count=$(jq -r ".populations[\"$pop_key\"]" "$EXPECTED" | tr -d '\r')
    actual_count=$(echo "$RESPONSE" | jq -r ".groups[0].populations[]? | select(.populationType == \"$pop_key\") | .count // empty" | tr -d '\r' | head -1)
    # Guard against both-null false positive: require expected to be a concrete value.
    if [ -z "$expected_count" ] || [ "$expected_count" = "null" ]; then
        echo "    ✗ $pop_key: expected.json has no value for this population" >&2
        fail=1
        continue
    fi
    if [ "${actual_count:-<null>}" = "$expected_count" ]; then
        echo "    ✓ $pop_key: $actual_count"
    else
        echo "    ✗ $pop_key: got ${actual_count:-<null>}, expected $expected_count" >&2
        fail=1
    fi
done < <(jq -r '.populations // {} | keys[]?' "$EXPECTED" | tr -d '\r')

# Provenance check (PAT-095 regression lock). Fetches reports for the measure
# and validates the latest row has non-null measureVersion + cqlHash + elmHash.
# Requires MEASURE_ID passed as 3rd arg.
check_provenance=$(jq -r '.checkProvenance // false' "$EXPECTED" | tr -d '\r')
if [ "$check_provenance" = "true" ]; then
    if [ -z "$MEASURE_ID" ]; then
        echo "    ✗ checkProvenance set but measureId not passed to assert.sh" >&2
        fail=1
    else
        API_BASE="${API_BASE:-http://localhost:8080/api}"
        reports_response=$(curl -sf \
            -H "Authorization: Bearer ${TOKEN:-}" \
            "$API_BASE/measures/$MEASURE_ID/reports" 2>&1) || {
            echo "    ✗ checkProvenance: GET /measures/$MEASURE_ID/reports failed: $reports_response" >&2
            fail=1
            exit $fail
        }
        # Take the first element (most recent — repo uses findByMeasureDefinitionIdOrderByCreatedAtDesc).
        cql_hash=$(echo "$reports_response" | jq -r '.[0].cqlHash // empty' | tr -d '\r')
        elm_hash=$(echo "$reports_response" | jq -r '.[0].elmHash // empty' | tr -d '\r')
        measure_version=$(echo "$reports_response" | jq -r '.[0].measureVersion // empty' | tr -d '\r')
        if [ -z "$cql_hash" ] || [ "$cql_hash" = "null" ]; then
            echo "    ✗ provenance.cqlHash: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.cqlHash: ${cql_hash:0:16}…"
        fi
        if [ -z "$elm_hash" ] || [ "$elm_hash" = "null" ]; then
            echo "    ✗ provenance.elmHash: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.elmHash: ${elm_hash:0:16}…"
        fi
        if [ -z "$measure_version" ] || [ "$measure_version" = "null" ]; then
            echo "    ✗ provenance.measureVersion: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.measureVersion: $measure_version"
        fi
    fi
fi

exit $fail
