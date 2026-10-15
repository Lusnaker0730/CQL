#!/usr/bin/env bash
# PAT-262 measure review (assigned reviewers + decisions + comment threads), end to end on the real stack:
#   1. a raw draft measure (owner = admin); GET /review is empty and the owner may assign;
#   2. the owner assigns the seeded `demo` user: the measure is shared with demo, the audit trail
#      records ASSIGN_REVIEWER; assigning the author is refused (four-eyes, HTTP 400);
#   3. a comment thread: admin opens it on the CQL section, demo replies (flattened to the root),
#      demo resolves it through the reply's id — the thread is resolved, no open threads remain;
#   4. a decision before submit is refused (HTTP 400: not in review);
#   5. submit → readiness warns REVIEWERS_PENDING but is ready;
#   6. demo requests changes → the measure is back in draft with the comment as reviewComment, the
#      readiness warns REVIEW_CHANGES_REQUESTED (not a blocker: the author must be able to resubmit);
#   7. resubmit → demo's decision is reset to pending (new round);
#   8. the author's own approve is refused (HTTP 403); demo approves the decision, then approves the
#      measure → active, approvedBy = demo;
#   9. demo, as an assigned reviewer, can read the audit trail (ASSIGN_REVIEWER + REVIEW_DECISION).
#
# Usage: lib/run-measure-review.sh <scenarioDir> <expected.json>
set -euo pipefail

SCENARIO_DIR="${1:?usage: run-measure-review.sh <scenarioDir> <expected.json>}"
EXPECTED="${2:?expected.json missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"
REVIEWER="${SMOKE_REVIEWER:-demo}"
REVIEWER_PASSWORD="${SMOKE_REVIEWER_PASSWORD:-password}"
AUTHOR="${SMOKE_USER:-admin}"

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
[ -f "$measure_file" ] || { echo "    ✗ missing $measure_file" >&2; exit 1; }
changes_comment=$(jq -r '.changesComment // "smoke: exclude hospice patients"' "$EXPECTED" | tr -d '\r')
reviewer_token="${REVIEWER_TOKEN:-$(SMOKE_USER="$REVIEWER" SMOKE_PASSWORD="$REVIEWER_PASSWORD" bash "$SCRIPT_DIR/auth.sh")}"

# 1. draft measure, empty review
measure_id=$(bash "$SCRIPT_DIR/create-measure-definition.sh" "$measure_file")
echo "  created draft measure #$measure_id" >&2
call GET "/measures/$measure_id/review" > "$tmp/review0.raw"
check "GET /review (HTTP 200)" "$(status_of "$tmp/review0.raw")" "200"
check "no reviewers yet" "$(body_of "$tmp/review0.raw" | jq -r '.summary.reviewers')" "0"
check "owner may assign" "$(body_of "$tmp/review0.raw" | jq -r '.canAssign')" "true"

# 2. assign the reviewer; the author may not be one
call PUT "/measures/$measure_id/review/reviewers" "$TOKEN" "$(jq -nc --arg u "$REVIEWER" '{usernames: [$u]}')" > "$tmp/assign.raw"
check "assign $REVIEWER (HTTP 200)" "$(status_of "$tmp/assign.raw")" "200"
check "reviewer listed, pending" "$(body_of "$tmp/assign.raw" | jq -r '.reviewers[0] | "\(.username):\(.decision)"')" "$REVIEWER:pending"
call GET "/measures/$measure_id" > "$tmp/measure1.raw"
check "assigning shared the measure with the reviewer" "$(body_of "$tmp/measure1.raw" | jq -r --arg u "$REVIEWER" '.sharedWith | index($u) != null')" "true"
check "access level is shared" "$(body_of "$tmp/measure1.raw" | jq -r '.accessLevel')" "shared"
call PUT "/measures/$measure_id/review/reviewers" "$TOKEN" "$(jq -nc --arg u "$REVIEWER" --arg a "$AUTHOR" '{usernames: [$u, $a]}')" > "$tmp/assign-author.raw"
check "assigning the author is refused (HTTP 400, four-eyes)" "$(status_of "$tmp/assign-author.raw")" "400"
check "refusal names four-eyes" "$(body_of "$tmp/assign-author.raw" | jq -r '[.details[]?] | any(test("four-eyes"))')" "true"

# 3. a comment thread, resolved from the reply
call POST "/measures/$measure_id/review/comments" "$TOKEN" '{"body":"Why 18 and not 20?","section":"cql"}' > "$tmp/c1.raw"
check "owner opens a thread (HTTP 200)" "$(status_of "$tmp/c1.raw")" "200"
root_id=$(body_of "$tmp/c1.raw" | jq -r '.id')
call POST "/measures/$measure_id/review/comments" "$reviewer_token" "$(jq -nc --argjson p "$root_id" '{body: "Guideline says adults", parentId: $p}')" > "$tmp/c2.raw"
check "reviewer replies (HTTP 200)" "$(status_of "$tmp/c2.raw")" "200"
check "reply is attached to the root" "$(body_of "$tmp/c2.raw" | jq -r '.parentId')" "$root_id"
check "reply inherits the section" "$(body_of "$tmp/c2.raw" | jq -r '.section')" "cql"
reply_id=$(body_of "$tmp/c2.raw" | jq -r '.id')
call POST "/measures/$measure_id/review/comments/$reply_id/resolve" "$reviewer_token" > "$tmp/resolve.raw"
check "resolving through the reply resolves the thread" "$(body_of "$tmp/resolve.raw" | jq -r '"\(.id):\(.resolved):\(.resolvedBy)"')" "$root_id:true:$REVIEWER"
call GET "/measures/$measure_id/review" "$reviewer_token" > "$tmp/review1.raw"
check "reviewer sees the thread with its reply" "$(body_of "$tmp/review1.raw" | jq -r '.comments[0].replies | length')" "1"
check "no open threads" "$(body_of "$tmp/review1.raw" | jq -r '.summary.openThreads')" "0"
check "reviewer flag" "$(body_of "$tmp/review1.raw" | jq -r '.reviewer')" "true"

# 4. decisions only in review
call POST "/measures/$measure_id/review/decision" "$reviewer_token" '{"decision":"approved"}' > "$tmp/early.raw"
check "decision before submit is refused (HTTP 400)" "$(status_of "$tmp/early.raw")" "400"

# 5. submit → pending reviewer is a warning
call POST "/measures/$measure_id/submit-for-review" "$TOKEN" '{"reason":"smoke PAT-262"}' > "$tmp/submit1.raw"
check "submit (HTTP 200)" "$(status_of "$tmp/submit1.raw")" "200"
call GET "/measures/$measure_id/approval-readiness" "$reviewer_token" > "$tmp/ready1.raw"
check "pending reviewer is a warning" "$(body_of "$tmp/ready1.raw" | jq -r '[.warnings[].code] | index("REVIEWERS_PENDING") != null')" "true"
check "…and the measure is ready" "$(body_of "$tmp/ready1.raw" | jq -r '.ready')" "true"
check "readiness review summary: 1 assigned, 1 pending" "$(body_of "$tmp/ready1.raw" | jq -r '"\(.review.assigned):\(.review.pending)"')" "1:1"

# 6. changes requested → back to draft
call POST "/measures/$measure_id/review/decision" "$reviewer_token" "$(jq -nc --arg c "$changes_comment" '{decision: "changes-requested", comment: $c}')" > "$tmp/changes.raw"
check "changes-requested (HTTP 200)" "$(status_of "$tmp/changes.raw")" "200"
check "measure returned to draft" "$(body_of "$tmp/changes.raw" | jq -r '.status')" "draft"
check "reviewer decision recorded" "$(body_of "$tmp/changes.raw" | jq -r '.reviewers[0].decision')" "changes-requested"
call GET "/measures/$measure_id" > "$tmp/measure2.raw"
check "review comment is the reviewer's text" "$(body_of "$tmp/measure2.raw" | jq -r '.reviewComment')" "$changes_comment"
call GET "/measures/$measure_id/approval-readiness" > "$tmp/ready2.raw"
check "in draft the request is a warning" "$(body_of "$tmp/ready2.raw" | jq -r '[.warnings[].code] | index("REVIEW_CHANGES_REQUESTED") != null')" "true"
check "…so the author can resubmit" "$(body_of "$tmp/ready2.raw" | jq -r '.ready')" "true"

# 7. resubmit → new round
call POST "/measures/$measure_id/submit-for-review" "$TOKEN" '{"reason":"smoke PAT-262 round 2"}' > "$tmp/submit2.raw"
check "resubmit (HTTP 200)" "$(status_of "$tmp/submit2.raw")" "200"
call GET "/measures/$measure_id/review" "$reviewer_token" > "$tmp/review2.raw"
check "decision reset to pending" "$(body_of "$tmp/review2.raw" | jq -r '.reviewers[0].decision')" "pending"

# 8. only the assigned reviewer approves
call POST "/measures/$measure_id/approve" "$TOKEN" '{"reason":"smoke PAT-262"}' > "$tmp/approve-author.raw"
check "author's approve refused (HTTP 403)" "$(status_of "$tmp/approve-author.raw")" "403"
call POST "/measures/$measure_id/review/decision" "$reviewer_token" '{"decision":"approved","comment":"smoke: looks right"}' > "$tmp/approved.raw"
check "reviewer approves the decision (HTTP 200)" "$(status_of "$tmp/approved.raw")" "200"
check "my decision = approved" "$(body_of "$tmp/approved.raw" | jq -r '.myDecision')" "approved"
call POST "/measures/$measure_id/approve" "$reviewer_token" '{"reason":"smoke PAT-262 reviewer"}' > "$tmp/approve.raw"
check "reviewer approve (HTTP 200)" "$(status_of "$tmp/approve.raw")" "200"
check "measure active" "$(body_of "$tmp/approve.raw" | jq -r '.status')" "active"
check "approvedBy is the reviewer" "$(body_of "$tmp/approve.raw" | jq -r '.approvedBy')" "$REVIEWER"

# 9. the reviewer reads the audit trail
call GET "/measures/$measure_id/audit" "$reviewer_token" > "$tmp/audit.raw"
check "reviewer reads the audit trail (HTTP 200)" "$(status_of "$tmp/audit.raw")" "200"
check "audit has ASSIGN_REVIEWER and REVIEW_DECISION" "$(body_of "$tmp/audit.raw" | jq -r '[.[].action] | (index("ASSIGN_REVIEWER") != null) and (index("REVIEW_DECISION") != null)')" "true"

exit $fail
