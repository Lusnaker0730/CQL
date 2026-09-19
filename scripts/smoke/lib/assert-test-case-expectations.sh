#!/usr/bin/env bash
# Assertions for the `test-case-expectations` scenario type (PAT-228).
#
# Inputs are raw envelopes from lib/test-case-raw.sh:
#   $1 run with the CORRECT structured expectation   → must be status "pass"
#   $2 run after the expectation was made WRONG      → must be status "fail", and the
#      mismatch must be the observation list (actual still reports the real values)
#   $3 create with an expectation naming a group the measure does not have → HTTP 400
#   $4 expected.json of the scenario (expectedObservations / wrongObservations)
#
# What this locks in end to end (real CQL engine, real published eCQM):
#   * the structured expectation is stored, read back and decides pass / fail;
#   * observation values come from the per-group wrapper define, one per episode;
#   * a test cannot silently pass against a group that does not exist.
set -euo pipefail

PASS_RAW="${1:?run-pass.raw missing}"
FAIL_RAW="${2:?run-fail.raw missing}"
BAD_RAW="${3:?bad-group.raw missing}"
EXPECTED="${4:?expected.json missing}"

status_of() { head -1 "$1" | tr -d '\r'; }
body_of()   { sed '1,/^---HTTP_STATUS_BODY---$/d' "$1"; }

fail=0
check() {  # check <description> <actual> <expected>
    if [ "$2" = "$3" ]; then
        echo "    ✓ $1" >&2
    else
        echo "    ✗ $1 — expected '$3', got '$2'" >&2
        fail=1
    fi
}

render() { jq -r '"[" + (map(if . == floor then (.|floor|tostring) else tostring end) | join(", ")) + "]"'; }
expected_obs=$(jq -c '.expectedObservations | sort' "$EXPECTED" | render)
wrong_obs=$(jq -c '.wrongObservations | sort' "$EXPECTED" | render)

# ── 1. correct expectation → pass ──
check "run (correct expectation) HTTP status" "$(status_of "$PASS_RAW")" "200"
pass_body=$(body_of "$PASS_RAW")
check "run (correct expectation) status" "$(echo "$pass_body" | jq -r '.status')" "pass"
check "structured comparison is what decided it (no legacy table)" \
    "$(echo "$pass_body" | jq -r '(.valueComparisons | length > 0) and (.comparisons == null)')" "true"
check "initial-population effective count" \
    "$(echo "$pass_body" | jq -r '.valueComparisons[] | select(.kind=="population" and .key=="initial-population") | .actual')" "1"
check "measure-population effective count (patient-based)" \
    "$(echo "$pass_body" | jq -r '.valueComparisons[] | select(.kind=="population" and .key=="measure-population") | .actual')" "1"
check "observation values (one per episode)" \
    "$(echo "$pass_body" | jq -r '.valueComparisons[] | select(.kind=="observation") | .actual')" "$expected_obs"
check "every compared item matches" \
    "$(echo "$pass_body" | jq -r '[.valueComparisons[].match] | all')" "true"

# ── 2. wrong expectation → fail, and it says why ──
check "run (wrong expectation) HTTP status" "$(status_of "$FAIL_RAW")" "200"
fail_body=$(body_of "$FAIL_RAW")
check "run (wrong expectation) status" "$(echo "$fail_body" | jq -r '.status')" "fail"
check "the observation row is the mismatch" \
    "$(echo "$fail_body" | jq -r '[.valueComparisons[] | select(.match == false) | .kind] | join(",")')" "observation"
check "mismatch shows the wrong expectation" \
    "$(echo "$fail_body" | jq -r '.valueComparisons[] | select(.kind=="observation") | .expected')" "$wrong_obs"
check "mismatch still reports the real values" \
    "$(echo "$fail_body" | jq -r '.valueComparisons[] | select(.kind=="observation") | .actual')" "$expected_obs"

# ── 3. expectation for a group the measure does not have → refused on save ──
check "unknown group is refused with HTTP 400" "$(status_of "$BAD_RAW")" "400"
check "refusal names the unknown group" \
    "$(body_of "$BAD_RAW" | jq -r '[.details[]? | select(test("Unknown population group"))] | length > 0')" "true"

exit $fail
