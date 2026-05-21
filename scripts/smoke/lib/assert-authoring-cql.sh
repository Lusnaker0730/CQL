#!/usr/bin/env bash
# Assert an authoring /cql endpoint response against expected.json.
#
# expected.json shape (type: "authoring-cql"):
#   {
#     "type": "authoring-cql",
#     "cqlContains": [                # optional — each string must appear in .cql
#         "include SharedLogic version '1.2.0' called shared",
#         "\"shared\".\"HasDiabetes\""
#     ],
#     "cqlDoesNotContain": [          # optional — each string must NOT appear
#         "externalCqlElement"
#     ]
#   }
#
# Usage:  lib/assert-authoring-cql.sh <response.json|-> <expected.json>
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert-authoring-cql.sh <response.json|-> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"

if [ "$RESPONSE_INPUT" = "-" ]; then
    RESPONSE=$(cat)
else
    RESPONSE=$(cat "$RESPONSE_INPUT")
fi

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

cql=$(echo "$RESPONSE" | jq -r '.cql // empty')
if [ -z "$cql" ]; then
    echo "    ✗ response missing .cql field" >&2
    echo "      response: $RESPONSE" >&2
    exit 1
fi

fail=0

# cqlContains — each string must appear verbatim in the generated CQL
contains_count=$(jq -r '.cqlContains | length // 0' "$EXPECTED")
if [ "$contains_count" != "0" ] && [ "$contains_count" != "null" ]; then
    for i in $(seq 0 $((contains_count - 1))); do
        needle=$(jq -r ".cqlContains[$i]" "$EXPECTED")
        case "$cql" in
            *"$needle"*)
                echo "    ✓ cql contains: ${needle:0:60}"
                ;;
            *)
                echo "    ✗ cql missing: $needle" >&2
                fail=1
                ;;
        esac
    done
fi

# cqlDoesNotContain — each string must be absent
excludes_count=$(jq -r '.cqlDoesNotContain | length // 0' "$EXPECTED")
if [ "$excludes_count" != "0" ] && [ "$excludes_count" != "null" ]; then
    for i in $(seq 0 $((excludes_count - 1))); do
        needle=$(jq -r ".cqlDoesNotContain[$i]" "$EXPECTED")
        case "$cql" in
            *"$needle"*)
                echo "    ✗ cql unexpectedly contains: $needle" >&2
                fail=1
                ;;
            *)
                echo "    ✓ cql does not contain: ${needle:0:60}"
                ;;
        esac
    done
fi

exit $fail
