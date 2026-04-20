#!/usr/bin/env bash
# Assert a CdsResponse against expected.json for CDS Hook scenarios.
#
# expected.json shape (type: "cds-hook"):
#   {
#     "type": "cds-hook",
#     "invocationStatus": 200,               # optional — assert HTTP status (default 200)
#     "cardCount": 1,                         # optional — exact count of cards
#     "cards": [                              # optional — per-card field assertions, ORDER-sensitive
#       {
#         "summary": "...",                   # exact match
#         "indicator": "info|warning|critical",
#         "sourceLabel": "...",               # optional
#         "detail": "..."                     # optional substring match (detail can be markdown)
#       }
#     ],
#     "debugPrefetchNonEmpty": true,          # optional — assert .debug.prefetchStatus has entries (dry-run test)
#     "expectNoCards": true                    # optional — explicit no-cards assertion (overrides cardCount)
#   }
#
# Usage:  lib/assert-cds.sh <response.json|-> <expected.json>
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert-cds.sh <response.json|-> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"

if [ "$RESPONSE_INPUT" = "-" ]; then
    RESPONSE=$(cat)
else
    RESPONSE=$(cat "$RESPONSE_INPUT")
fi

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

fail=0

# cardCount
expected_count=$(jq -r '.cardCount // empty' "$EXPECTED" | tr -d '\r')
expect_no_cards=$(jq -r '.expectNoCards // false' "$EXPECTED" | tr -d '\r')
actual_count=$(echo "$RESPONSE" | jq -r '.cards | length')

if [ "$expect_no_cards" = "true" ]; then
    if [ "$actual_count" = "0" ]; then
        echo "    ✓ cards: 0 (expected none)"
    else
        echo "    ✗ cards: got $actual_count, expected 0" >&2
        fail=1
    fi
elif [ -n "$expected_count" ]; then
    if [ "$actual_count" = "$expected_count" ]; then
        echo "    ✓ cardCount: $actual_count"
    else
        echo "    ✗ cardCount: got $actual_count, expected $expected_count" >&2
        fail=1
    fi
fi

# Per-card assertions (order-sensitive)
card_spec_count=$(jq -r '.cards | length // 0' "$EXPECTED" 2>/dev/null)
if [ "$card_spec_count" != "0" ] && [ "$card_spec_count" != "null" ]; then
    for i in $(seq 0 $((card_spec_count - 1))); do
        for field in summary indicator; do
            exp=$(jq -r ".cards[$i].$field // empty" "$EXPECTED" | tr -d '\r')
            [ -z "$exp" ] && continue
            act=$(echo "$RESPONSE" | jq -r ".cards[$i].$field // empty" | tr -d '\r')
            if [ "$act" = "$exp" ]; then
                echo "    ✓ cards[$i].$field: $act"
            else
                echo "    ✗ cards[$i].$field: got '$act', expected '$exp'" >&2
                fail=1
            fi
        done
        # sourceLabel: nested at cards[].source.label per CDS Hooks spec — expected.json
        # still uses flat "sourceLabel" key for readability.
        exp_source=$(jq -r ".cards[$i].sourceLabel // empty" "$EXPECTED" | tr -d '\r')
        if [ -n "$exp_source" ]; then
            act_source=$(echo "$RESPONSE" | jq -r ".cards[$i].source.label // empty" | tr -d '\r')
            if [ "$act_source" = "$exp_source" ]; then
                echo "    ✓ cards[$i].source.label: $act_source"
            else
                echo "    ✗ cards[$i].source.label: got '$act_source', expected '$exp_source'" >&2
                fail=1
            fi
        fi
        # detail: substring match (markdown body)
        exp_detail=$(jq -r ".cards[$i].detail // empty" "$EXPECTED" | tr -d '\r')
        if [ -n "$exp_detail" ]; then
            act_detail=$(echo "$RESPONSE" | jq -r ".cards[$i].detail // empty" | tr -d '\r')
            case "$act_detail" in
                *"$exp_detail"*)
                    echo "    ✓ cards[$i].detail contains: '${exp_detail:0:40}…'"
                    ;;
                *)
                    echo "    ✗ cards[$i].detail: got '${act_detail:0:80}', does not contain '$exp_detail'" >&2
                    fail=1
                    ;;
            esac
        fi
    done
fi

# Debug prefetch status (dry-run scenarios)
expect_debug_prefetch=$(jq -r '.debugPrefetchNonEmpty // false' "$EXPECTED" | tr -d '\r')
if [ "$expect_debug_prefetch" = "true" ]; then
    prefetch_len=$(echo "$RESPONSE" | jq -r '.debug.prefetchStatus | length // 0')
    if [ "$prefetch_len" != "0" ] && [ "$prefetch_len" != "null" ]; then
        echo "    ✓ debug.prefetchStatus has $prefetch_len entries"
    else
        echo "    ✗ debug.prefetchStatus is empty or missing (dry-run should populate this)" >&2
        fail=1
    fi
fi

exit $fail
