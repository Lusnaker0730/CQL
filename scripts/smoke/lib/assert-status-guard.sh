#!/usr/bin/env bash
# Assertions for measure-status-guard scenarios (PAT-219).
#
# Takes the two evaluate-raw.sh envelopes captured by run.sh — one from the
# draft phase, one after approval — and expected.json:
#   {
#     "type": "measure-status-guard",
#     "draftHttpStatus": 409,                        # optional, default 409
#     "draftErrorField": "Measure Not Evaluable",    # optional — exact match on .error
#     "draftMessageContains": "active",              # optional — substring of .message
#     "approvedHttpStatus": 200,                     # optional, default 200
#     "score": 4.0, "populations": {...}             # forwarded to assert.sh for the approved body
#   }
#
# Phase 1 locks the refusal contract (status + ErrorResponse shape) so an EHR
# integrator can tell "not approved yet" apart from a real failure. Phase 2
# reuses assert.sh so the post-approval result is checked exactly like any
# other eCQM scenario — the guard must lift cleanly, not just stop throwing.
#
# Usage:  lib/assert-status-guard.sh <draft.raw> <approved.raw> <expected.json>
set -euo pipefail

DRAFT_RAW_FILE="${1:?usage: assert-status-guard.sh <draft.raw> <approved.raw> <expected.json>}"
APPROVED_RAW_FILE="${2:?approved.raw path missing}"
EXPECTED="${3:?expected.json path missing}"
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

split_status() { head -n1 "$1" | tr -d '\r'; }
split_body()   { awk '/^---HTTP_STATUS_BODY---$/{seen=1; next} seen' "$1"; }

fail=0

# ── Phase 1: draft must be refused ──
draft_status=$(split_status "$DRAFT_RAW_FILE")
draft_body=$(split_body "$DRAFT_RAW_FILE")
exp_draft_status=$(jq -r '.draftHttpStatus // 409' "$EXPECTED" | tr -d '\r')
if [ "$draft_status" = "$exp_draft_status" ]; then
    echo "    ✓ draft evaluate refused with HTTP $draft_status"
else
    echo "    ✗ draft evaluate: got HTTP $draft_status, expected $exp_draft_status" >&2
    echo "      body: $(echo "$draft_body" | head -c 300)" >&2
    fail=1
fi

exp_error=$(jq -r '.draftErrorField // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$exp_error" ]; then
    actual_error=$(echo "$draft_body" | jq -r '.error // empty' 2>/dev/null | tr -d '\r')
    if [ "$actual_error" = "$exp_error" ]; then
        echo "    ✓ draft error: '$actual_error'"
    else
        echo "    ✗ draft error: got '${actual_error:-<null>}', expected '$exp_error'" >&2
        fail=1
    fi
fi

exp_msg=$(jq -r '.draftMessageContains // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$exp_msg" ]; then
    actual_msg=$(echo "$draft_body" | jq -r '.message // empty' 2>/dev/null | tr -d '\r')
    if echo "$actual_msg" | grep -qF -- "$exp_msg"; then
        echo "    ✓ draft message mentions '$exp_msg'"
    else
        echo "    ✗ draft message missing '$exp_msg': ${actual_msg:-<null>}" >&2
        fail=1
    fi
fi

# ── Phase 2: approved must evaluate normally ──
approved_status=$(split_status "$APPROVED_RAW_FILE")
approved_body=$(split_body "$APPROVED_RAW_FILE")
exp_approved_status=$(jq -r '.approvedHttpStatus // 200' "$EXPECTED" | tr -d '\r')
if [ "$approved_status" = "$exp_approved_status" ]; then
    echo "    ✓ approved evaluate returned HTTP $approved_status"
else
    echo "    ✗ approved evaluate: got HTTP $approved_status, expected $exp_approved_status" >&2
    echo "      body: $(echo "$approved_body" | head -c 300)" >&2
    fail=1
fi

# Same rule as evaluate.sh: a 200 with status=error is still a failed evaluation.
eval_status=$(echo "$approved_body" | jq -r '.status // empty' 2>/dev/null | tr -d '\r')
if [ "$eval_status" = "error" ]; then
    echo "    ✗ approved evaluate reported status=error: $(echo "$approved_body" | jq -r '.errorMessage // "no errorMessage"')" >&2
    fail=1
fi

if [ "$fail" -eq 0 ]; then
    # Populations / score / any other eCQM knobs live in the shared assert.
    if ! echo "$approved_body" | bash "$LIB_DIR/assert.sh" - "$EXPECTED"; then
        fail=1
    fi
fi

exit "$fail"
