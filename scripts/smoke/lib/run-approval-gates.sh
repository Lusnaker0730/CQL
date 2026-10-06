#!/usr/bin/env bash
# PAT-249 approval gates, end to end on the real stack:
#   1. a raw draft measure (POST /api/measures, owner = admin) is ready while it has no test cases
#      (warning NO_TEST_CASES only);
#   2. a test case with a wrong expectation is created, validated (must be `valid` — a bad fixture
#      would trip a different gate) and run → fail;
#   3. GET /approval-readiness now reports ready=false with TEST_CASE_NOT_PASSING, and the owner is
#      told four-eyes blocks them;
#   4. submit-for-review is refused with HTTP 409 `Measure Not Ready` listing the blocker;
#   5. the expectation is fixed, the run passes, readiness is ready=true, submit succeeds;
#   6. the owner's own approve is refused with HTTP 403 `Approval Not Allowed` (four-eyes);
#   7. after sharing with the reviewer, the reviewer's approve lands the measure `active`.
#
# Usage: lib/run-approval-gates.sh <scenarioDir> <expected.json>
set -euo pipefail

SCENARIO_DIR="${1:?usage: run-approval-gates.sh <scenarioDir> <expected.json>}"
EXPECTED="${2:?expected.json missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"
REVIEWER="${SMOKE_REVIEWER:-demo}"
REVIEWER_PASSWORD="${SMOKE_REVIEWER_PASSWORD:-password}"

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

# call <METHOD> <path> [token] [body-json] → envelope "STATUS\n---HTTP_STATUS_BODY---\nBODY" on stdout
call() {
    local method="$1" path="$2" token="${3:-$TOKEN}" body="${4-}"
    local args=(-s -X "$method" "$API_BASE$path" -H "Content-Type: application/json" -H "Authorization: Bearer $token" -w "\n__HTTP_STATUS__%{http_code}")
    [ -n "$body" ] && args+=(--data-binary "$body")
    local response
    response=$(curl "${args[@]}") || { echo "    ✗ transport error on $method $path" >&2; return 1; }
    printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')" "$(echo "$response" | sed '$d')"
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

measure_file="$SCENARIO_DIR/measure.json"
bundle_file="$SCENARIO_DIR/testcase-bundle.json"
for f in "$measure_file" "$bundle_file"; do
    [ -f "$f" ] || { echo "    ✗ missing $f" >&2; exit 1; }
done
blocker_code=$(jq -r '.blockerCode // "TEST_CASE_NOT_PASSING"' "$EXPECTED" | tr -d '\r')
not_ready_error=$(jq -r '.notReadyError // "Measure Not Ready"' "$EXPECTED" | tr -d '\r')
four_eyes_error=$(jq -r '.fourEyesError // "Approval Not Allowed"' "$EXPECTED" | tr -d '\r')

# 1. draft measure, no test cases → ready with the NO_TEST_CASES warning
measure_id=$(bash "$SCRIPT_DIR/create-measure-definition.sh" "$measure_file")
echo "  created draft measure #$measure_id" >&2
call GET "/measures/$measure_id/approval-readiness" > "$tmp/ready0.raw"
check "readiness (HTTP 200)" "$(status_of "$tmp/ready0.raw")" "200"
check "no test cases → ready (warning only)" "$(body_of "$tmp/ready0.raw" | jq -r '.ready')" "true"
check "no test cases → NO_TEST_CASES warning" "$(body_of "$tmp/ready0.raw" | jq -r '[.warnings[].code] | index("NO_TEST_CASES") != null')" "true"
check "four-eyes enabled" "$(body_of "$tmp/ready0.raw" | jq -r '.fourEyes.enabled')" "true"
check "owner is told they cannot approve" "$(body_of "$tmp/ready0.raw" | jq -r '.fourEyes.selfApprovalBlocked')" "true"

# 2. a test case with a wrong expectation: validate (sync) and run → fail. The measure is a raw
#    definition without group definitions, so the legacy expectation is keyed by DEFINE NAME
#    ("Initial Population"), not by population type. The fixture Patient carries an identifier
#    because the TW Core Patient profile requires one — the gate under test is the run, not validation.
jq -n --rawfile bundle "$bundle_file" '{title: "adult but expected out", patientBundleJson: $bundle, expectedPopulations: {"Initial Population": false}}' > "$tmp/create.json"
bash "$SCRIPT_DIR/test-case-raw.sh" POST "$measure_id" "" "$tmp/create.json" > "$tmp/create.raw"
check "test case created (HTTP 200)" "$(status_of "$tmp/create.raw")" "200"
tc_id=$(body_of "$tmp/create.raw" | jq -r '.id // empty')
[ -n "$tc_id" ] || { echo "    ✗ test case was not created: $(head -c 300 "$tmp/create.raw")" >&2; exit 1; }
bash "$SCRIPT_DIR/test-case-raw.sh" POST "$measure_id" "/$tc_id/validate" > "$tmp/validate.raw"
check "test case bundle is FHIR-valid (so only the run gate is in play)" "$(body_of "$tmp/validate.raw" | jq -r '.validationStatus')" "valid"
bash "$SCRIPT_DIR/test-case-raw.sh" POST "$measure_id" "/$tc_id/run" > "$tmp/run1.raw"
check "wrong expectation → run fails" "$(body_of "$tmp/run1.raw" | jq -r '.status')" "fail"

# 3. readiness now blocks
call GET "/measures/$measure_id/approval-readiness" > "$tmp/ready1.raw"
check "failing test case → not ready" "$(body_of "$tmp/ready1.raw" | jq -r '.ready')" "false"
check "blocker code $blocker_code" "$(body_of "$tmp/ready1.raw" | jq -r --arg c "$blocker_code" '[.blockers[].code] | index($c) != null')" "true"
check "blocker names the test case" "$(body_of "$tmp/ready1.raw" | jq -r --arg c "$blocker_code" '.blockers[] | select(.code == $c) | .items[0]')" "adult but expected out (fail)"
check "tally: 0 of 1 passing" "$(body_of "$tmp/ready1.raw" | jq -r '"\(.testCases.passed)/\(.testCases.total)"')" "0/1"

# 4. submit-for-review refused with the blockers in details
call POST "/measures/$measure_id/submit-for-review" "$TOKEN" '{"reason":"smoke PAT-249"}' > "$tmp/submit1.raw"
check "submit while not ready (HTTP 409)" "$(status_of "$tmp/submit1.raw")" "409"
check "submit refusal error field" "$(body_of "$tmp/submit1.raw" | jq -r '.error')" "$not_ready_error"
check "submit refusal lists the blocker" "$(body_of "$tmp/submit1.raw" | jq -r '(.details | length) >= 1')" "true"
call GET "/measures/$measure_id" > "$tmp/after-submit1.raw"
check "measure still draft" "$(body_of "$tmp/after-submit1.raw" | jq -r '.status')" "draft"

# 5. fix the expectation → pass → ready → submit succeeds
body_of "$tmp/create.raw" | jq '.expectedPopulations = {"Initial Population": true}' > "$tmp/fix.json"
bash "$SCRIPT_DIR/test-case-raw.sh" PUT "$measure_id" "/$tc_id" "$tmp/fix.json" > "$tmp/fix.raw"
check "expectation fixed (HTTP 200)" "$(status_of "$tmp/fix.raw")" "200"
bash "$SCRIPT_DIR/test-case-raw.sh" POST "$measure_id" "/$tc_id/run" > "$tmp/run2.raw"
check "fixed expectation → run passes" "$(body_of "$tmp/run2.raw" | jq -r '.status')" "pass"
call GET "/measures/$measure_id/approval-readiness" > "$tmp/ready2.raw"
check "passing test case → ready" "$(body_of "$tmp/ready2.raw" | jq -r '.ready')" "true"
call POST "/measures/$measure_id/submit-for-review" "$TOKEN" '{"reason":"smoke PAT-249"}' > "$tmp/submit2.raw"
check "submit when ready (HTTP 200)" "$(status_of "$tmp/submit2.raw")" "200"
check "measure in review" "$(body_of "$tmp/submit2.raw" | jq -r '.status')" "in-review"

# 6. the owner / submitter cannot approve
call POST "/measures/$measure_id/approve" "$TOKEN" '{"reason":"smoke PAT-249"}' > "$tmp/approve-self.raw"
check "self-approval refused (HTTP 403)" "$(status_of "$tmp/approve-self.raw")" "403"
check "self-approval error field" "$(body_of "$tmp/approve-self.raw" | jq -r '.error')" "$four_eyes_error"
call GET "/measures/$measure_id" > "$tmp/after-self.raw"
check "measure still in review" "$(body_of "$tmp/after-self.raw" | jq -r '.status')" "in-review"

# 7. another reviewer approves
call POST "/measures/$measure_id/share" "$TOKEN" "$(jq -nc --arg u "$REVIEWER" '{targetUsername: $u}')" > "$tmp/share.raw"
check "shared with reviewer $REVIEWER (HTTP 200)" "$(status_of "$tmp/share.raw")" "200"
reviewer_token="${REVIEWER_TOKEN:-$(SMOKE_USER="$REVIEWER" SMOKE_PASSWORD="$REVIEWER_PASSWORD" bash "$SCRIPT_DIR/auth.sh")}"
call GET "/measures/$measure_id/approval-readiness" "$reviewer_token" > "$tmp/ready-reviewer.raw"
check "reviewer is not blocked by four-eyes" "$(body_of "$tmp/ready-reviewer.raw" | jq -r '.fourEyes.selfApprovalBlocked')" "false"
call POST "/measures/$measure_id/approve" "$reviewer_token" '{"reason":"smoke PAT-249 reviewer"}' > "$tmp/approve.raw"
check "reviewer approve (HTTP 200)" "$(status_of "$tmp/approve.raw")" "200"
check "measure active" "$(body_of "$tmp/approve.raw" | jq -r '.status')" "active"
check "approvedBy is the reviewer" "$(body_of "$tmp/approve.raw" | jq -r '.approvedBy')" "$REVIEWER"

exit $fail
