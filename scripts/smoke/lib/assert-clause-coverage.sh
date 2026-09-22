#!/usr/bin/env bash
# Assertions for the `clause-coverage` scenario type (PAT-232).
#
# Inputs are raw envelopes from lib/test-case-raw.sh:
#   $1 debug-mode run of the test case WITH encounters     → carries clauseCoverage
#   $2 normal run of the same test case                    → no clauseCoverage (population
#      evaluation never pays for the per-node handler)
#   $3 debug-mode run of the test case WITHOUT encounters  → clauseCoverage that covers
#      strictly fewer clauses (per-encounter clauses never ran)
#   $4 POST /test-cases/coverage                           → the union over both runs
#
# What this locks in end to end (real cql-engine 5.x BreakpointHandler, real published
# eCQM CQL):
#   * the static clause universe and the dynamic hits describe the same CQL text;
#   * a clause the data never reaches stays uncovered — coverage is not trivially 100%;
#   * the measure-wide view is the union of the per-test-case runs, on the same text.
set -euo pipefail

FULL_RAW="${1:?run-full-debug.raw missing}"
PLAIN_RAW="${2:?run-full-plain.raw missing}"
EMPTY_RAW="${3:?run-empty-debug.raw missing}"
UNION_RAW="${4:?coverage.raw missing}"

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

# Internal consistency of one ClauseCoverage object (stdin): totals add up, every clause
# has a parsable ELM locator, covered == clauses with hits, percent is derived from them.
consistent() {
    jq -r '
        def pct: if .totalClauses == 0 then 0 else ((.coveredClauses * 1000 / .totalClauses) | round) / 10 end;
        (.cql | length > 0)
        and (.totalClauses > 0)
        and (.totalClauses == ([.statements[].totalClauses] | add))
        and (.coveredClauses == ([.statements[].coveredClauses] | add))
        and (.coveredClauses == ([.statements[].clauses[] | select(.hits > 0)] | length))
        and (.totalClauses == ([.statements[].clauses[]] | length))
        and ([.statements[].clauses[].locator | test("^[0-9]+:[0-9]+(-[0-9]+:[0-9]+)?$")] | all)
        and ((.percent - pct) | fabs < 0.11)'
}

# ── 1. debug run carries a consistent clause coverage ──
check "debug run HTTP status" "$(status_of "$FULL_RAW")" "200"
full=$(body_of "$FULL_RAW" | jq -c '.clauseCoverage // empty')
check "debug run carries clauseCoverage" "$([ -n "$full" ] && echo yes || echo no)" "yes"
check "debug run status (expectation still decides pass / fail)" "$(body_of "$FULL_RAW" | jq -r '.status')" "pass"
if [ -n "$full" ]; then
    check "clauseCoverage is internally consistent" "$(echo "$full" | consistent)" "true"
    check "the patient with encounters covers every clause" \
        "$(echo "$full" | jq -r '.coveredClauses == .totalClauses')" "true"
fi

# ── 2. a normal run does not pay for it ──
check "normal run HTTP status" "$(status_of "$PLAIN_RAW")" "200"
check "normal run has no clauseCoverage" "$(body_of "$PLAIN_RAW" | jq -r '.clauseCoverage == null')" "true"

# ── 3. no data → per-encounter clauses stay uncovered ──
check "empty-patient debug run HTTP status" "$(status_of "$EMPTY_RAW")" "200"
empty=$(body_of "$EMPTY_RAW" | jq -c '.clauseCoverage // empty')
check "empty-patient run carries clauseCoverage" "$([ -n "$empty" ] && echo yes || echo no)" "yes"
if [ -n "$empty" ] && [ -n "$full" ]; then
    check "empty-patient coverage is internally consistent" "$(echo "$empty" | consistent)" "true"
    check "same clause universe as the full run" \
        "$(jq -n --argjson a "$full" --argjson b "$empty" '$a.totalClauses == $b.totalClauses and $a.cql == $b.cql')" "true"
    check "empty patient covers strictly fewer clauses" \
        "$(jq -n --argjson a "$full" --argjson b "$empty" '$b.coveredClauses < $a.coveredClauses')" "true"
    check "at least one statement has an uncovered clause" \
        "$(echo "$empty" | jq -r '[.statements[] | select(.coveredClauses < .totalClauses)] | length > 0')" "true"
fi

# ── 4. measure-wide union ──
check "coverage endpoint HTTP status" "$(status_of "$UNION_RAW")" "200"
union_body=$(body_of "$UNION_RAW")
check "both test cases were executed" "$(echo "$union_body" | jq -r '"\(.testCases)/\(.executed)/\(.passed)"')" "2/2/2"
union=$(echo "$union_body" | jq -c '.coverage // empty')
check "union carries a coverage" "$([ -n "$union" ] && echo yes || echo no)" "yes"
if [ -n "$union" ] && [ -n "$full" ] && [ -n "$empty" ]; then
    check "union is internally consistent" "$(echo "$union" | consistent)" "true"
    check "union is over the same CQL text and clause universe" \
        "$(jq -n --argjson a "$full" --argjson u "$union" '$a.totalClauses == $u.totalClauses and $a.cql == $u.cql')" "true"
    check "union covers at least what each run covered" \
        "$(jq -n --argjson a "$full" --argjson b "$empty" --argjson u "$union" \
            '$u.coveredClauses >= $a.coveredClauses and $u.coveredClauses > $b.coveredClauses')" "true"
    check "union hits add up across runs" \
        "$(jq -n --argjson a "$full" --argjson b "$empty" --argjson u "$union" \
            '([$a, $b, $u] | map([.statements[].clauses[].hits] | add)) as [$ha, $hb, $hu] | $hu == $ha + $hb')" "true"
fi

exit $fail
