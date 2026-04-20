#!/usr/bin/env bash
# Assert a CqlExecutionResponse against expected.json for cql-execute scenarios.
#
# expected.json shape (type: "cql-execute"):
#   {
#     "type": "cql-execute",
#     "success": true,                                         # optional — assert .success
#     "expressionTracesMinCount": 3,                           # optional — .debugTrace.expressionTraces|length >= N
#     "expressionTraceRequiredFields":                         # optional — each entry must have these keys non-null
#         ["name", "resultType", "evaluationTimeMs", "order"],
#     "retrieveTracesMinCount": 0,                             # optional — .debugTrace.retrieveTraces|length >= N
#     "elmJsonNonEmpty": true,                                 # optional — .debugTrace.elmJson must be a non-empty string
#     "totalTimeMsPresent": true                               # optional — .debugTrace.totalTimeMs must be a number
#   }
#
# Assertions target field *presence and type*, not specific values — debug
# trace schema is diagnostic UX, not clinical output. Over-locking forces
# smoke churn on every authoring-UX improvement.
#
# Usage:  lib/assert-cql-debug.sh <response.json|-> <expected.json>
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert-cql-debug.sh <response.json|-> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"

if [ "$RESPONSE_INPUT" = "-" ]; then
    RESPONSE=$(cat)
else
    RESPONSE=$(cat "$RESPONSE_INPUT")
fi

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

fail=0

# success
exp_success=$(jq -r '.success // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$exp_success" ]; then
    act_success=$(echo "$RESPONSE" | jq -r '.success // empty')
    if [ "$act_success" = "$exp_success" ]; then
        echo "    ✓ success: $act_success"
    else
        echo "    ✗ success: got '$act_success', expected '$exp_success'" >&2
        # Surface errors to help debug test failures (don't over-print on success)
        echo "      errors: $(echo "$RESPONSE" | jq -c '.errors // []')" >&2
        fail=1
    fi
fi

# expressionTracesMinCount
exp_min_traces=$(jq -r '.expressionTracesMinCount // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$exp_min_traces" ]; then
    act_traces=$(echo "$RESPONSE" | jq -r '.debugTrace.expressionTraces | length // 0')
    if [ "$act_traces" -ge "$exp_min_traces" ]; then
        echo "    ✓ expressionTraces: $act_traces (>= $exp_min_traces)"
    else
        echo "    ✗ expressionTraces: got $act_traces, expected >= $exp_min_traces" >&2
        fail=1
    fi
fi

# expressionTraceRequiredFields — first entry must have all named fields non-null
req_fields_count=$(jq -r '.expressionTraceRequiredFields | length // 0' "$EXPECTED" 2>/dev/null)
if [ "$req_fields_count" != "0" ] && [ "$req_fields_count" != "null" ]; then
    # Only check first trace entry — all entries share the same schema, checking
    # one catches schema regressions without inflating scenario maintenance.
    first_trace=$(echo "$RESPONSE" | jq -c '.debugTrace.expressionTraces[0] // null')
    if [ "$first_trace" = "null" ]; then
        echo "    ✗ expressionTraceRequiredFields: expressionTraces[0] missing — cannot validate fields" >&2
        fail=1
    else
        for i in $(seq 0 $((req_fields_count - 1))); do
            field=$(jq -r ".expressionTraceRequiredFields[$i]" "$EXPECTED" | tr -d '\r')
            value=$(echo "$first_trace" | jq -r ".$field // \"__MISSING__\"")
            if [ "$value" = "__MISSING__" ] || [ "$value" = "null" ]; then
                echo "    ✗ expressionTraces[0].$field: missing or null" >&2
                fail=1
            else
                echo "    ✓ expressionTraces[0].$field present: ${value:0:40}"
            fi
        done
    fi
fi

# retrieveTracesMinCount
exp_min_retrieves=$(jq -r '.retrieveTracesMinCount // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$exp_min_retrieves" ]; then
    act_retrieves=$(echo "$RESPONSE" | jq -r '.debugTrace.retrieveTraces | length // 0')
    if [ "$act_retrieves" -ge "$exp_min_retrieves" ]; then
        echo "    ✓ retrieveTraces: $act_retrieves (>= $exp_min_retrieves)"
    else
        echo "    ✗ retrieveTraces: got $act_retrieves, expected >= $exp_min_retrieves" >&2
        fail=1
    fi
fi

# elmJsonNonEmpty — debugMode=true should populate compiled ELM
exp_elm=$(jq -r '.elmJsonNonEmpty // false' "$EXPECTED" | tr -d '\r')
if [ "$exp_elm" = "true" ]; then
    elm_len=$(echo "$RESPONSE" | jq -r '.debugTrace.elmJson | length // 0')
    if [ "$elm_len" -gt 0 ]; then
        echo "    ✓ debugTrace.elmJson: $elm_len chars"
    else
        echo "    ✗ debugTrace.elmJson: empty or missing (debugMode=true should populate)" >&2
        fail=1
    fi
fi

# totalTimeMsPresent — number (not null)
exp_total=$(jq -r '.totalTimeMsPresent // false' "$EXPECTED" | tr -d '\r')
if [ "$exp_total" = "true" ]; then
    total_type=$(echo "$RESPONSE" | jq -r '.debugTrace.totalTimeMs | type')
    if [ "$total_type" = "number" ]; then
        echo "    ✓ debugTrace.totalTimeMs: $(echo "$RESPONSE" | jq -r '.debugTrace.totalTimeMs') ms"
    else
        echo "    ✗ debugTrace.totalTimeMs: got type '$total_type', expected number" >&2
        fail=1
    fi
fi

exit $fail
