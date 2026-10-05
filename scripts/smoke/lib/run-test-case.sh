#!/usr/bin/env bash
# Create one test case for a published measure from the scenario's `testCase` spec, run it,
# and assert the run (PAT-242 / PAT-243). Used by the `ecqm` scenario type after the
# evaluation assertions when expected.json carries a `testCase` object:
#
#   "testCase": {
#     "bundle": "testcase-bundle.json",          # FHIR collection Bundle in the scenario dir
#     "title": "…",
#     "expectedValues": { "groups": [ … ] },     # structured expectation stored on the test case
#     "expectStatus": "pass",                    # run status
#     "expectMeasurementPeriodStart": "2024-01-01",   # optional: the period the run reports
#     "expectMeasurementPeriodEnd": "2024-12-31",
#     "expectActuals": { "numerator": "1" },     # optional: population → actual (first group)
#     "expectValidation": true,                  # optional (PAT-245): POST …/validate must answer valid | invalid
#     "expectRoundTrip": true                    # optional (PAT-247): export zip → import-bundles must recreate the expectation
#   }
#
# Usage: lib/run-test-case.sh <measureId> <scenarioDir> <expected.json>
set -euo pipefail

MEASURE_ID="${1:?usage: run-test-case.sh <measureId> <scenarioDir> <expected.json>}"
SCENARIO_DIR="${2:?scenarioDir missing}"
EXPECTED="${3:?expected.json missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api}"

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

bundle_file="$SCENARIO_DIR/$(jq -r '.testCase.bundle' "$EXPECTED" | tr -d '\r')"
[ -f "$bundle_file" ] || { echo "    ✗ test case bundle not found: $bundle_file" >&2; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

jq -n --rawfile bundle "$bundle_file" \
      --arg title "$(jq -r '.testCase.title // "smoke test case"' "$EXPECTED" | tr -d '\r')" \
      --argjson expected "$(jq -c '.testCase.expectedValues' "$EXPECTED")" \
      '{title: $title, patientBundleJson: $bundle, expectedValues: $expected}' > "$tmp/create.json"

bash "$SCRIPT_DIR/test-case-raw.sh" POST "$MEASURE_ID" "" "$tmp/create.json" > "$tmp/create.raw"
check "test case created (HTTP 200)" "$(status_of "$tmp/create.raw")" "200"
tc_id=$(body_of "$tmp/create.raw" | jq -r '.id // empty')
if [ -z "$tc_id" ]; then
    echo "    ✗ test case was not created: $(head -c 400 "$tmp/create.raw")" >&2
    exit 1
fi
echo "  created test case #$tc_id" >&2

bash "$SCRIPT_DIR/test-case-raw.sh" POST "$MEASURE_ID" "/$tc_id/run" > "$tmp/run.raw"
check "test case run (HTTP 200)" "$(status_of "$tmp/run.raw")" "200"
run_body=$(body_of "$tmp/run.raw")

expect_status=$(jq -r '.testCase.expectStatus // "pass"' "$EXPECTED" | tr -d '\r')
check "test case status" "$(echo "$run_body" | jq -r '.status')" "$expect_status"
if [ "$(echo "$run_body" | jq -r '.status')" != "$expect_status" ]; then
    echo "$run_body" | jq -c '.valueComparisons // .comparisons // .errorMessage' >&2
fi

expect_mp_start=$(jq -r '.testCase.expectMeasurementPeriodStart // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$expect_mp_start" ]; then
    check "run used the measure's measurement period start" \
        "$(echo "$run_body" | jq -r '.measurementPeriodStart // empty')" "$expect_mp_start"
fi
expect_mp_end=$(jq -r '.testCase.expectMeasurementPeriodEnd // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$expect_mp_end" ]; then
    check "run used the measure's measurement period end" \
        "$(echo "$run_body" | jq -r '.measurementPeriodEnd // empty')" "$expect_mp_end"
fi

while IFS= read -r pop; do
    [ -n "$pop" ] || continue
    expected_actual=$(jq -r ".testCase.expectActuals[\"$pop\"]" "$EXPECTED" | tr -d '\r')
    actual=$(echo "$run_body" | jq -r ".valueComparisons[]? | select(.kind == \"population\" and .key == \"$pop\") | .actual" | head -1)
    check "actual $pop" "$actual" "$expected_actual"
done < <(jq -r '.testCase.expectActuals // {} | keys[]?' "$EXPECTED" | tr -d '\r')

# PAT-245: the real HAPI validator ran over the bundle and produced a verdict (valid or invalid —
# which one depends on the profiles loaded in this stack; "error" / "pending" would be a bug).
if [ "$(jq -r '.testCase.expectValidation // false' "$EXPECTED" | tr -d '\r')" = "true" ]; then
    bash "$SCRIPT_DIR/test-case-raw.sh" POST "$MEASURE_ID" "/$tc_id/validate" > "$tmp/validate.raw"
    check "validate (HTTP 200)" "$(status_of "$tmp/validate.raw")" "200"
    vstatus=$(body_of "$tmp/validate.raw" | jq -r '.validationStatus // empty')
    if [ "$vstatus" = "valid" ] || [ "$vstatus" = "invalid" ]; then
        echo "    ✓ validation produced a verdict ($vstatus; $(body_of "$tmp/validate.raw" | jq -c '.validation | {totalResources, invalidResources, errorCount, warningCount}'))" >&2
    else
        echo "    ✗ validation verdict — expected valid|invalid, got '$vstatus'" >&2; fail=1
    fi
fi

# PAT-247: the MADiE-compatible exchange round trip — export the test case as a zip of FHIR bundles
# (patient resources + test-case-cqfm MeasureReport), import the zip back, and the copy must carry
# the same structured expectation, be named after the Patient (MADiE's convention) and run to the
# same verdict. Needs no unzip on the host: the server does both halves.
if [ "$(jq -r '.testCase.expectRoundTrip // false' "$EXPECTED" | tr -d '')" = "true" ]; then
    export_status=$(curl -s -o "$tmp/export.zip" -w '%{http_code}'         -H "Authorization: Bearer $TOKEN" "$API_BASE/measures/$MEASURE_ID/test-cases/export?ids=$tc_id")
    check "export zip (HTTP 200)" "$export_status" "200"
    check "export is a zip archive" "$(head -c 2 "$tmp/export.zip")" "PK"

    import_resp=$(curl -s -X POST -H "Authorization: Bearer $TOKEN"         -F "file=@$tmp/export.zip;type=application/zip" -w "
__HTTP_STATUS__%{http_code}"         "$API_BASE/measures/$MEASURE_ID/test-cases/import-bundles")
    import_status=$(echo "$import_resp" | tail -1 | sed 's/__HTTP_STATUS__//')
    import_body=$(echo "$import_resp" | sed '$d')
    check "import-bundles (HTTP 200)" "$import_status" "200"
    check "round trip imported 1 test case" "$(echo "$import_body" | jq -r '.successCount')" "1"
    check "round trip import had no warnings" "$(echo "$import_body" | jq -r '.warnings | length')" "0"
    check "round trip kept the structured expectation"         "$(echo "$import_body" | jq -c '.imported[0].expectedValues.groups[0].populations')"         "$(jq -c '.testCase.expectedValues.groups[0].populations' "$EXPECTED")"
    patient_given=$(jq -r '[.entry[] | select(.resource.resourceType == "Patient")][0].resource.name[0].given // [] | join(" ")' "$bundle_file" | tr -d '')
    if [ -n "$patient_given" ]; then
        check "round trip titled the copy after the Patient's given name" "$(echo "$import_body" | jq -r '.imported[0].title')" "$patient_given"
    fi
    check "round trip left the MeasureReport out of the stored bundle"         "$(echo "$import_body" | jq -r '.imported[0].patientBundleJson | fromjson | [.entry[].resource.resourceType] | index("MeasureReport") // "none"')" "none"
    copy_id=$(echo "$import_body" | jq -r '.imported[0].id // empty')
    if [ -n "$copy_id" ]; then
        bash "$SCRIPT_DIR/test-case-raw.sh" POST "$MEASURE_ID" "/$copy_id/run" > "$tmp/run-copy.raw"
        check "round-tripped copy runs to the same status" "$(body_of "$tmp/run-copy.raw" | jq -r '.status')" "$expect_status"
    else
        echo "    ✗ round trip produced no test case id: $(echo "$import_body" | head -c 400)" >&2; fail=1
    fi
fi

exit $fail
