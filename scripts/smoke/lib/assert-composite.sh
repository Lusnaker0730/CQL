#!/usr/bin/env bash
# PAT-259: assert one composite evaluation response against expected.json `.composites.<method>`:
# the first group is `composite-summary` with the expected score (tolerance `.scoreTolerance`) and population
# counts, `componentScores` lists every component, and the status is complete.
#
# Usage:  lib/assert-composite.sh <response.json> <expected.json> <method>
set -u
RESPONSE="${1:?usage: assert-composite.sh <response.json> <expected.json> <method>}"
EXPECTED="${2:?expected.json missing}"
METHOD="${3:?method missing}"
fail=0
check() {   # check <label> <jq boolean expression> [file]
    local label="$1" expr="$2" file="${3:-$RESPONSE}"
    if [ "$(jq -r "$expr" "$file" 2>/dev/null | tr -d '\r')" = "true" ]; then
        echo "    ✓ [$METHOD] $label" >&2
    else
        echo "    ✗ [$METHOD] $label — got: $(jq -c '{status, score: .groups[0].measureScore, populations: ([.groups[0].populations[]? | {(.populationType): .count}] | add), components: (.componentScores | length)}' "$file" 2>/dev/null | head -c 300)" >&2
        fail=1
    fi
}
tol=$(jq -r '.scoreTolerance // 0.001' "$EXPECTED" | tr -d '\r')
score=$(jq -r ".composites[\"$METHOD\"].score" "$EXPECTED" | tr -d '\r')
components=$(jq -r '.componentCount // 2' "$EXPECTED" | tr -d '\r')

check "status complete" '.status == "complete"'
check "first group is composite-summary" '.groups[0].groupId == "composite-summary"'
check "composite score = $score (±$tol)" "(.groups[0].measureScore | . != null) and ((.groups[0].measureScore - $score) | fabs) <= $tol"
check "componentScores lists $components components" ".componentScores | length == $components"
check "every component has a score" '[.componentScores[].score] | all(. != null)'
for pop in $(jq -r ".composites[\"$METHOD\"].populations | keys[]" "$EXPECTED" | tr -d '\r'); do
    want=$(jq -r ".composites[\"$METHOD\"].populations[\"$pop\"]" "$EXPECTED" | tr -d '\r')
    check "summary $pop = $want" "[.groups[0].populations[] | select(.populationType == \"$pop\") | .count] == [$want]"
done
check "no subject ids leak into a summary report" '[.groups[].populations[]?.subjectIds] | all(. == null)'
exit $fail
