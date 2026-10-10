#!/usr/bin/env bash
# PAT-253 measure set lineage + edit locks, end to end on the real stack. The seeded `demo` user
# (ROLE_USER) owns everything here; the seeded `admin` (ROLE_ADMIN) is the other editor.
#   1. POST /api/measures (as demo) returns a measureSetId; demo shares v1 with admin, submits it,
#      admin approves (four-eyes) → active.
#   2. POST /{id}/version keeps the set id; renaming v2 keeps it in v1's history (2 entries, two
#      names); a new measure created with the OLD name + another version is its own set.
#   3. Sharing is a property of the set: unshare on v1 removes admin from v2, share on v2 puts
#      admin back on v1.
#   4. A test case locked by demo refuses admin's PUT / DELETE / lock / unlock with 409 `Locked`
#      (details carry lockedBy + lockExpiresAt); demo's own PUT goes through and keeps the lock;
#      after demo unlocks, admin's PUT succeeds. The case is deleted before v2 is submitted.
#   5. The same for a CQL library: lock → admin's PUT / DELETE 409 → unlock → admin's PUT 200.
#   6. Approving the renamed v2 retires v1 — supersede keys on the set, not the name.
#
# Usage: lib/run-measure-set-locks.sh <scenarioDir> <expected.json>
set -euo pipefail

SCENARIO_DIR="${1:?usage: run-measure-set-locks.sh <scenarioDir> <expected.json>}"
EXPECTED="${2:?expected.json missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api}"
: "${TOKEN:?TOKEN env var must be set}"
REVIEWER="${SMOKE_REVIEWER:-demo}"
REVIEWER_PASSWORD="${SMOKE_REVIEWER_PASSWORD:-password}"

status_of() { head -1 "$1" | tr -d '\r'; }
body_of()   { sed '1,/^---HTTP_STATUS_BODY---$/d' "$1"; }

fail=0
check() {  # check <description> <actual> <expected> [raw-envelope-file: body shown on mismatch]
    if [ "$2" = "$3" ]; then
        echo "    ✓ $1" >&2
    else
        echo "    ✗ $1 — expected '$3', got '$2'" >&2
        [ -n "${4-}" ] && [ -f "$4" ] && echo "      body: $(body_of "$4" | head -c 300 | tr -d '
')" >&2
        fail=1
    fi
}

# call <METHOD> <path> [token] [body-json] → envelope "STATUS\n---HTTP_STATUS_BODY---\nBODY" on stdout
call() {
    local method="$1" path="$2" token="${3:-$TOKEN}" body="${4-}"
    local args=(-s -X "$method" "$API_BASE$path" -H "Content-Type: application/json" -H "Authorization: Bearer $token" -w "\n__HTTP_STATUS__%{http_code}")
    if [ -n "$body" ]; then
        # Always send the body from a file: on Windows a non-ASCII body (the fixtures' em dash)
        # passed as a curl argument is re-encoded through the ANSI code page and rejected as JSON.
        local body_file="$tmp/call-body-$RANDOM$RANDOM.json"
        printf '%s' "$body" > "$body_file"
        args+=(--data-binary "@$body_file")
    fi
    local response
    response=$(curl "${args[@]}") || { echo "    ✗ transport error on $method $path" >&2; return 1; }
    printf '%s\n---HTTP_STATUS_BODY---\n%s\n' "$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')" "$(echo "$response" | sed '$d')"
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

measure_file="$SCENARIO_DIR/measure.json"
library_file="$SCENARIO_DIR/library.json"
bundle_file="$SCENARIO_DIR/testcase-bundle.json"
for f in "$measure_file" "$library_file" "$bundle_file"; do
    [ -f "$f" ] || { echo "    ✗ missing $f" >&2; exit 1; }
done
locked_error=$(jq -r '.lockedError // "Locked"' "$EXPECTED" | tr -d '\r')
renamed_name=$(jq -r '.renamedName // "SmokeMeasureSetRenamed"' "$EXPECTED" | tr -d '\r')
library_id=$(jq -r '.libraryId // "SmokeSetLockLib-1.0.0"' "$EXPECTED" | tr -d '\r')

# run.sh logs the reviewer in once per run (REVIEWER_TOKEN); fall back to logging in when used alone.
OWNER_TOKEN="${REVIEWER_TOKEN:-$(SMOKE_USER="$REVIEWER" SMOKE_PASSWORD="$REVIEWER_PASSWORD" bash "$SCRIPT_DIR/auth.sh")}"
ADMIN_TOKEN="$TOKEN"

# 1. demo creates v1 → it carries a measure set; admin (shared) approves it
measure_id=$(TOKEN="$OWNER_TOKEN" bash "$SCRIPT_DIR/create-measure-definition.sh" "$measure_file")
echo "  created draft measure #$measure_id as $REVIEWER" >&2
call GET "/measures/$measure_id" "$OWNER_TOKEN" > "$tmp/v1.raw"
set_id=$(body_of "$tmp/v1.raw" | jq -r '.measureSetId // empty' | tr -d '\r')
check "measure carries a measureSetId" "$([ -n "$set_id" ] && echo yes || echo no)" "yes"
call POST "/measures/$measure_id/share" "$OWNER_TOKEN" '{"targetUsername":"admin"}' > "$tmp/share1.raw"
check "share v1 with admin (HTTP 200)" "$(status_of "$tmp/share1.raw")" "200" "$tmp/share1.raw"
call POST "/measures/$measure_id/submit-for-review" "$OWNER_TOKEN" '{"reason":"smoke PAT-253"}' > "$tmp/submit1.raw"
check "submit v1 (HTTP 200)" "$(status_of "$tmp/submit1.raw")" "200" "$tmp/submit1.raw"
call POST "/measures/$measure_id/approve" "$ADMIN_TOKEN" '{"reason":"smoke PAT-253"}' > "$tmp/approve1.raw"
check "admin approves v1 → active" "$(body_of "$tmp/approve1.raw" | jq -r '.status')" "active"

# 2. a minor version keeps the set; renaming it keeps it in the lineage
call POST "/measures/$measure_id/version?type=minor" "$OWNER_TOKEN" > "$tmp/v2.raw"
check "create version (HTTP 200)" "$(status_of "$tmp/v2.raw")" "200" "$tmp/v2.raw"
v2_id=$(body_of "$tmp/v2.raw" | jq -r '.id // empty' | tr -d '\r')
[ -n "$v2_id" ] || { echo "    ✗ version was not created: $(head -c 300 "$tmp/v2.raw")" >&2; exit 1; }
check "v2 is 1.1.0" "$(body_of "$tmp/v2.raw" | jq -r '.version')" "1.1.0"
check "v2 is in v1's measure set" "$(body_of "$tmp/v2.raw" | jq -r '.measureSetId')" "$set_id"
body_of "$tmp/v2.raw" | jq --arg n "$renamed_name" '.name = $n' > "$tmp/rename.json"
call PUT "/measures/$v2_id" "$OWNER_TOKEN" "$(cat "$tmp/rename.json")" > "$tmp/rename.raw"
check "rename v2 (HTTP 200)" "$(status_of "$tmp/rename.raw")" "200" "$tmp/rename.raw"
check "v2 renamed" "$(body_of "$tmp/rename.raw" | jq -r '.name')" "$renamed_name"
call GET "/measures/$measure_id/history" "$OWNER_TOKEN" > "$tmp/history.raw"
check "history of v1 has both versions despite the rename" "$(body_of "$tmp/history.raw" | jq -r 'length')" "2"
check "history lists the renamed v2 first" "$(body_of "$tmp/history.raw" | jq -r '.[0].name')" "$renamed_name"
check "history keeps v1's own name" "$(body_of "$tmp/history.raw" | jq -r '.[1].name')" "SmokeMeasureSet"
jq '.version = "9.0.0"' "$measure_file" > "$tmp/stranger.json"
stranger_id=$(TOKEN="$OWNER_TOKEN" bash "$SCRIPT_DIR/create-measure-definition.sh" "$tmp/stranger.json")
call GET "/measures/$stranger_id/history" "$OWNER_TOKEN" > "$tmp/stranger-history.raw"
check "a new measure with the old name is its own lineage" "$(body_of "$tmp/stranger-history.raw" | jq -r 'length')" "1"
call GET "/measures/$stranger_id" "$OWNER_TOKEN" > "$tmp/stranger.raw"
check "…with its own measure set" "$(body_of "$tmp/stranger.raw" | jq -r --arg s "$set_id" '.measureSetId != ($s | tonumber)')" "true"

# 3. sharing spans the set
call POST "/measures/$measure_id/unshare" "$OWNER_TOKEN" '{"targetUsername":"admin"}' > "$tmp/unshare.raw"
check "unshare admin on v1 (HTTP 200)" "$(status_of "$tmp/unshare.raw")" "200" "$tmp/unshare.raw"
call GET "/measures/$v2_id" "$OWNER_TOKEN" > "$tmp/v2-after-unshare.raw"
check "unshare on v1 reaches v2" "$(body_of "$tmp/v2-after-unshare.raw" | jq -r '(.sharedWith // []) | index("admin") == null')" "true"
call POST "/measures/$v2_id/share" "$OWNER_TOKEN" '{"targetUsername":"admin"}' > "$tmp/share2.raw"
check "share admin on v2 (HTTP 200)" "$(status_of "$tmp/share2.raw")" "200" "$tmp/share2.raw"
call GET "/measures/$measure_id" "$OWNER_TOKEN" > "$tmp/v1-after-share.raw"
check "share on v2 reaches v1" "$(body_of "$tmp/v1-after-share.raw" | jq -r '(.sharedWith // []) | index("admin") != null')" "true"

# 4. test case edit lock (on the draft v2)
jq -n --rawfile bundle "$bundle_file" '{title: "lock case", patientBundleJson: $bundle, expectedPopulations: {"Initial Population": true}}' > "$tmp/tc.json"
TOKEN="$OWNER_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" POST "$v2_id" "" "$tmp/tc.json" > "$tmp/tc-create.raw"
check "test case created (HTTP 200)" "$(status_of "$tmp/tc-create.raw")" "200" "$tmp/tc-create.raw"
tc_id=$(body_of "$tmp/tc-create.raw" | jq -r '.id // empty' | tr -d '\r')
[ -n "$tc_id" ] || { echo "    ✗ test case was not created: $(head -c 300 "$tmp/tc-create.raw")" >&2; exit 1; }
call POST "/measures/$v2_id/test-cases/$tc_id/lock" "$OWNER_TOKEN" > "$tmp/tc-lock.raw"
check "demo locks the test case (HTTP 200)" "$(status_of "$tmp/tc-lock.raw")" "200" "$tmp/tc-lock.raw"
check "lock holder is $REVIEWER" "$(body_of "$tmp/tc-lock.raw" | jq -r '.lockedBy')" "$REVIEWER"
check "lock carries an expiry" "$(body_of "$tmp/tc-lock.raw" | jq -r '.lockExpiresAt != null')" "true"
body_of "$tmp/tc-create.raw" | jq '.title = "edited by admin"' > "$tmp/tc-edit-admin.json"
TOKEN="$ADMIN_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" PUT "$v2_id" "/$tc_id" "$tmp/tc-edit-admin.json" > "$tmp/tc-put-admin.raw"
check "admin's PUT while locked (HTTP 409)" "$(status_of "$tmp/tc-put-admin.raw")" "409" "$tmp/tc-put-admin.raw"
check "refusal error field" "$(body_of "$tmp/tc-put-admin.raw" | jq -r '.error')" "$locked_error"
check "refusal details name the holder" "$(body_of "$tmp/tc-put-admin.raw" | jq -r --arg u "$REVIEWER" '(.details // []) | map(startswith("lockedBy: " + $u)) | any')" "true"
check "refusal details carry the expiry" "$(body_of "$tmp/tc-put-admin.raw" | jq -r '(.details // []) | map(startswith("lockExpiresAt: ")) | any')" "true"
TOKEN="$ADMIN_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" DELETE "$v2_id" "/$tc_id" > "$tmp/tc-delete-admin.raw"
check "admin's DELETE while locked (HTTP 409)" "$(status_of "$tmp/tc-delete-admin.raw")" "409" "$tmp/tc-delete-admin.raw"
call POST "/measures/$v2_id/test-cases/$tc_id/lock" "$ADMIN_TOKEN" > "$tmp/tc-lock-admin.raw"
check "admin's lock while locked (HTTP 409)" "$(status_of "$tmp/tc-lock-admin.raw")" "409" "$tmp/tc-lock-admin.raw"
call POST "/measures/$v2_id/test-cases/$tc_id/unlock" "$ADMIN_TOKEN" > "$tmp/tc-unlock-admin.raw"
check "admin's unlock (not holder, not owner) (HTTP 409)" "$(status_of "$tmp/tc-unlock-admin.raw")" "409" "$tmp/tc-unlock-admin.raw"
body_of "$tmp/tc-create.raw" | jq '.title = "edited by holder"' > "$tmp/tc-edit-holder.json"
TOKEN="$OWNER_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" PUT "$v2_id" "/$tc_id" "$tmp/tc-edit-holder.json" > "$tmp/tc-put-holder.raw"
check "holder's own PUT (HTTP 200)" "$(status_of "$tmp/tc-put-holder.raw")" "200" "$tmp/tc-put-holder.raw"
check "holder's PUT keeps the lock" "$(body_of "$tmp/tc-put-holder.raw" | jq -r '.lockedBy')" "$REVIEWER"
call POST "/measures/$v2_id/test-cases/$tc_id/unlock" "$OWNER_TOKEN" > "$tmp/tc-unlock.raw"
check "holder unlocks (HTTP 200)" "$(status_of "$tmp/tc-unlock.raw")" "200" "$tmp/tc-unlock.raw"
check "lock released" "$(body_of "$tmp/tc-unlock.raw" | jq -r '.lockedBy')" "null"
TOKEN="$ADMIN_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" PUT "$v2_id" "/$tc_id" "$tmp/tc-edit-admin.json" > "$tmp/tc-put-admin2.raw"
check "admin's PUT after unlock (HTTP 200)" "$(status_of "$tmp/tc-put-admin2.raw")" "200" "$tmp/tc-put-admin2.raw"
check "admin's edit landed" "$(body_of "$tmp/tc-put-admin2.raw" | jq -r '.title')" "edited by admin"
# SecurityConfig makes every DELETE under /api/measures/** admin-only (the owner cannot delete its
# own test case — pre-existing, not part of PAT-253), so the clean-up delete is admin's.
TOKEN="$ADMIN_TOKEN" bash "$SCRIPT_DIR/test-case-raw.sh" DELETE "$v2_id" "/$tc_id" > "$tmp/tc-delete.raw"
check "test case deleted before submit (HTTP 204, as admin)" "$(status_of "$tmp/tc-delete.raw")" "204" "$tmp/tc-delete.raw"

# 5. CQL library edit lock
call POST "/cql/libraries" "$OWNER_TOKEN" "$(cat "$library_file")" > "$tmp/lib-create.raw"
check "library created (HTTP 201)" "$(status_of "$tmp/lib-create.raw")" "201" "$tmp/lib-create.raw"
check "library id" "$(body_of "$tmp/lib-create.raw" | jq -r '.id')" "$library_id"
call POST "/cql/libraries/$library_id/lock" "$OWNER_TOKEN" > "$tmp/lib-lock.raw"
check "demo locks the library (HTTP 200)" "$(status_of "$tmp/lib-lock.raw")" "200" "$tmp/lib-lock.raw"
check "library lock holder is $REVIEWER" "$(body_of "$tmp/lib-lock.raw" | jq -r '.lockedBy')" "$REVIEWER"
call PUT "/cql/libraries/$library_id" "$ADMIN_TOKEN" "$(jq '.description = "edited by admin"' "$library_file")" > "$tmp/lib-put-admin.raw"
check "admin's library PUT while locked (HTTP 409)" "$(status_of "$tmp/lib-put-admin.raw")" "409" "$tmp/lib-put-admin.raw"
check "library refusal error field" "$(body_of "$tmp/lib-put-admin.raw" | jq -r '.error')" "$locked_error"
call DELETE "/cql/libraries/$library_id" "$ADMIN_TOKEN" > "$tmp/lib-delete-admin.raw"
check "admin's library DELETE while locked (HTTP 409)" "$(status_of "$tmp/lib-delete-admin.raw")" "409" "$tmp/lib-delete-admin.raw"
call POST "/cql/libraries/$library_id/unlock" "$OWNER_TOKEN" > "$tmp/lib-unlock.raw"
check "demo unlocks the library (HTTP 200)" "$(status_of "$tmp/lib-unlock.raw")" "200" "$tmp/lib-unlock.raw"
check "library lock released" "$(body_of "$tmp/lib-unlock.raw" | jq -r '.lockedBy')" "null"
call PUT "/cql/libraries/$library_id" "$ADMIN_TOKEN" "$(jq '.description = "edited by admin"' "$library_file")" > "$tmp/lib-put-admin2.raw"
check "admin's library PUT after unlock (HTTP 200)" "$(status_of "$tmp/lib-put-admin2.raw")" "200" "$tmp/lib-put-admin2.raw"
check "admin's library edit landed" "$(body_of "$tmp/lib-put-admin2.raw" | jq -r '.description')" "edited by admin"

# 6. approving the renamed v2 retires v1 (supersede keys on the set)
call POST "/measures/$v2_id/submit-for-review" "$OWNER_TOKEN" '{"reason":"smoke PAT-253 v2"}' > "$tmp/submit2.raw"
check "submit v2 (HTTP 200)" "$(status_of "$tmp/submit2.raw")" "200" "$tmp/submit2.raw"
call POST "/measures/$v2_id/approve" "$ADMIN_TOKEN" '{"reason":"smoke PAT-253 v2"}' > "$tmp/approve2.raw"
check "admin approves renamed v2 → active" "$(body_of "$tmp/approve2.raw" | jq -r '.status')" "active"
call GET "/measures/$measure_id" "$OWNER_TOKEN" > "$tmp/v1-final.raw"
check "v1 retired by the renamed v2 (set-based supersede)" "$(body_of "$tmp/v1-final.raw" | jq -r '.status')" "retired"
call GET "/measures/$stranger_id" "$OWNER_TOKEN" > "$tmp/stranger-final.raw"
check "the namesake outside the set is untouched" "$(body_of "$tmp/stranger-final.raw" | jq -r '.status')" "draft"

exit $fail
