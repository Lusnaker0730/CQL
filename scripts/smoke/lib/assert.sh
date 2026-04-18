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
#     }
#   }
#
# Usage:  lib/assert.sh <response.json-or-stdin> <expected.json>
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert.sh <response.json|-> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"

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

exit $fail
