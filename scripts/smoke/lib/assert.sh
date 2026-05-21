#!/usr/bin/env bash
# Compare an evaluation response against an expected.json fixture.
#
# expected.json shape:
#   {
#     "score": 0.6,              # optional — compared with 1e-6 tolerance if numeric
#     "scoreTolerance": 0.001,   # optional — override default tolerance
#     "populations": {
#       "initial-population": 7,
#       "denominator": 5,
#       "numerator": 3
#     },
#     "checkProvenance": true    # optional — follow-up GET /api/measures/{id}/reports
#                                # to verify PAT-095 provenance fields (measureVersion,
#                                # cqlHash, elmHash) are non-null on the saved report.
#                                # Requires measureId as the optional 3rd arg.
#   }
#
# Usage:  lib/assert.sh <response.json-or-stdin> <expected.json> [measureId]
set -euo pipefail

RESPONSE_INPUT="${1:?usage: assert.sh <response.json|-> <expected.json> [measureId]}"
EXPECTED="${2:?expected.json path missing}"
MEASURE_ID="${3:-}"

if [ "$RESPONSE_INPUT" = "-" ]; then
    RESPONSE=$(cat)
else
    RESPONSE=$(cat "$RESPONSE_INPUT")
fi

[ -f "$EXPECTED" ] || { echo "expected.json not found: $EXPECTED" >&2; exit 1; }

fail=0

# Score
expected_score=$(jq -r '.score // empty' "$EXPECTED")
if [ -n "$expected_score" ]; then
    tolerance=$(jq -r '.scoreTolerance // 0.001' "$EXPECTED")
    actual_score=$(echo "$RESPONSE" | jq -r '.groups[0].measureScore // empty')
    if [ -z "$actual_score" ]; then
        echo "    ✗ score: expected $expected_score, got null" >&2
        fail=1
    else
        diff=$(awk -v a="$actual_score" -v b="$expected_score" 'BEGIN{d=a-b; if (d<0) d=-d; print d}')
        within=$(awk -v d="$diff" -v t="$tolerance" 'BEGIN{print (d<=t)?1:0}')
        if [ "$within" = "1" ]; then
            echo "    ✓ score: $actual_score (expected $expected_score, Δ=$diff)"
        else
            echo "    ✗ score: $actual_score (expected $expected_score, Δ=$diff > tolerance $tolerance)" >&2
            fail=1
        fi
    fi
fi

# Empty-IP / no-eligible-patients edge case: score MUST be null. Asserts the
# divide-by-zero / empty-cohort path returns null cleanly instead of NaN, 0.0,
# or a 5xx. Locks MeasureScoreCalculator.calculateProportionScore line 68-70.
expect_score_null=$(jq -r '.expectScoreNull // false' "$EXPECTED" | tr -d '\r')
if [ "$expect_score_null" = "true" ]; then
    actual_score=$(echo "$RESPONSE" | jq -r '.groups[0].measureScore // empty')
    if [ -z "$actual_score" ] || [ "$actual_score" = "null" ]; then
        echo "    ✓ score is null (expected for empty IP / zero denominator)"
    else
        echo "    ✗ score: expected null (empty IP), got $actual_score" >&2
        fail=1
    fi
fi

# Populations (keyed by population-type).
# Use `tr -d '\r'` to strip Windows CRLF line endings from jq output — without it,
# pop_key becomes "denominator\r" and the lookup quietly returns null on both
# sides, producing false-positive passes.
while IFS= read -r pop_key; do
    [ -z "$pop_key" ] && continue
    expected_count=$(jq -r ".populations[\"$pop_key\"]" "$EXPECTED" | tr -d '\r')
    actual_count=$(echo "$RESPONSE" | jq -r ".groups[0].populations[]? | select(.populationType == \"$pop_key\") | .count // empty" | tr -d '\r' | head -1)
    # Guard against both-null false positive: require expected to be a concrete value.
    if [ -z "$expected_count" ] || [ "$expected_count" = "null" ]; then
        echo "    ✗ $pop_key: expected.json has no value for this population" >&2
        fail=1
        continue
    fi
    if [ "${actual_count:-<null>}" = "$expected_count" ]; then
        echo "    ✓ $pop_key: $actual_count"
    else
        echo "    ✗ $pop_key: got ${actual_count:-<null>}, expected $expected_count" >&2
        fail=1
    fi
done < <(jq -r '.populations // {} | keys[]?' "$EXPECTED" | tr -d '\r')

# Multi-group assertions. expected.json `groups[]` lets a scenario lock multiple
# population groups (eCQM with stratified clinical pathways / ratio dual-IP). Each
# entry: {groupId, populations, score?, scoreTolerance?}. Groups are matched by
# groupId; order in the response doesn't matter. Use this when a measure has more
# than one group; falls back to top-level `populations` + `score` for groups[0].
expected_groups_count=$(jq -r '.groups // [] | length' "$EXPECTED" | tr -d '\r')
if [ "$expected_groups_count" -gt 0 ] 2>/dev/null; then
    for gi in $(seq 0 $((expected_groups_count - 1))); do
        gid=$(jq -r ".groups[$gi].groupId" "$EXPECTED" | tr -d '\r')
        actual_group=$(echo "$RESPONSE" | jq -c ".groups[]? | select(.groupId == \"$gid\")" | head -1)
        if [ -z "$actual_group" ]; then
            echo "    ✗ group $gid: not found in response" >&2
            fail=1
            continue
        fi
        # Per-group populations
        while IFS= read -r pk; do
            [ -z "$pk" ] && continue
            exp_pc=$(jq -r ".groups[$gi].populations[\"$pk\"]" "$EXPECTED" | tr -d '\r')
            act_pc=$(echo "$actual_group" | jq -r ".populations[]? | select(.populationType == \"$pk\") | .count // empty" | tr -d '\r' | head -1)
            if [ "${act_pc:-<null>}" = "$exp_pc" ]; then
                echo "    ✓ group $gid.$pk: $act_pc"
            else
                echo "    ✗ group $gid.$pk: got ${act_pc:-<null>}, expected $exp_pc" >&2
                fail=1
            fi
        done < <(jq -r ".groups[$gi].populations | keys[]?" "$EXPECTED" | tr -d '\r')
        # Per-group score (optional)
        exp_gs=$(jq -r ".groups[$gi].score // empty" "$EXPECTED" | tr -d '\r')
        if [ -n "$exp_gs" ]; then
            gtol=$(jq -r ".groups[$gi].scoreTolerance // 0.5" "$EXPECTED" | tr -d '\r')
            act_gs=$(echo "$actual_group" | jq -r '.measureScore // empty')
            if [ -z "$act_gs" ] || [ "$act_gs" = "null" ]; then
                echo "    ✗ group $gid.score: expected $exp_gs, got null" >&2
                fail=1
            else
                gd=$(awk -v a="$act_gs" -v b="$exp_gs" 'BEGIN{d=a-b; if (d<0) d=-d; print d}')
                gw=$(awk -v d="$gd" -v t="$gtol" 'BEGIN{print (d<=t)?1:0}')
                if [ "$gw" = "1" ]; then
                    echo "    ✓ group $gid.score: $act_gs (Δ=$gd)"
                else
                    echo "    ✗ group $gid.score: $act_gs (expected $exp_gs, Δ=$gd > $gtol)" >&2
                    fail=1
                fi
            fi
        fi
        # Per-group stratifier assertions (multi-group + stratifier coverage). Each entry:
        # {strataId, expectedStrata: [{strataValue, populations, score?, scoreTolerance?}]}.
        # Locks BUG #474 follow-up — verifies per-group stratifier results attach to the
        # right group rather than mixing into a single shared bucket.
        gstr_count=$(jq -r ".groups[$gi].stratifiers // [] | length" "$EXPECTED" | tr -d '\r')
        if [ "$gstr_count" -gt 0 ] 2>/dev/null; then
            for si in $(seq 0 $((gstr_count - 1))); do
                gsid=$(jq -r ".groups[$gi].stratifiers[$si].strataId" "$EXPECTED" | tr -d '\r')
                gstol=$(jq -r ".groups[$gi].stratifiers[$si].scoreTolerance // 0.5" "$EXPECTED" | tr -d '\r')
                gstr_actual=$(echo "$actual_group" | jq -c ".stratifiers[]? | select(.strataId == \"$gsid\")" 2>/dev/null || echo "")
                if [ -z "$gstr_actual" ]; then
                    echo "    ✗ group $gid.stratifier $gsid: not found in response" >&2
                    fail=1
                    continue
                fi
                strata_count=$(jq -r ".groups[$gi].stratifiers[$si].expectedStrata | length" "$EXPECTED")
                for sj in $(seq 0 $((strata_count - 1))); do
                    sv=$(jq -r ".groups[$gi].stratifiers[$si].expectedStrata[$sj].strataValue" "$EXPECTED" | tr -d '\r')
                    actual_stratum=$(echo "$gstr_actual" | jq -c "select(.strataValue == \"$sv\")" | head -1)
                    if [ -z "$actual_stratum" ]; then
                        echo "    ✗ group $gid.stratifier $gsid stratum '$sv': not found" >&2
                        fail=1
                        continue
                    fi
                    while IFS= read -r pkk; do
                        [ -z "$pkk" ] && continue
                        epc=$(jq -r ".groups[$gi].stratifiers[$si].expectedStrata[$sj].populations[\"$pkk\"]" "$EXPECTED" | tr -d '\r')
                        apc=$(echo "$actual_stratum" | jq -r ".populations[]? | select(.populationType == \"$pkk\") | .count // empty" | tr -d '\r' | head -1)
                        if [ "${apc:-<null>}" = "$epc" ]; then
                            echo "    ✓ group $gid.$gsid[$sv].$pkk: $apc"
                        else
                            echo "    ✗ group $gid.$gsid[$sv].$pkk: got ${apc:-<null>}, expected $epc" >&2
                            fail=1
                        fi
                    done < <(jq -r ".groups[$gi].stratifiers[$si].expectedStrata[$sj].populations | keys[]?" "$EXPECTED" | tr -d '\r')
                    expected_ss=$(jq -r ".groups[$gi].stratifiers[$si].expectedStrata[$sj].score // empty" "$EXPECTED" | tr -d '\r')
                    if [ -n "$expected_ss" ]; then
                        actual_ss=$(echo "$actual_stratum" | jq -r '.measureScore // empty')
                        if [ -z "$actual_ss" ] || [ "$actual_ss" = "null" ]; then
                            echo "    ✗ group $gid.$gsid[$sv].score: expected $expected_ss, got null" >&2
                            fail=1
                        else
                            ssd=$(awk -v a="$actual_ss" -v b="$expected_ss" 'BEGIN{d=a-b; if (d<0) d=-d; print d}')
                            ssw=$(awk -v d="$ssd" -v t="$gstol" 'BEGIN{print (d<=t)?1:0}')
                            if [ "$ssw" = "1" ]; then
                                echo "    ✓ group $gid.$gsid[$sv].score: $actual_ss (Δ=$ssd)"
                            else
                                echo "    ✗ group $gid.$gsid[$sv].score: $actual_ss (expected $expected_ss, Δ=$ssd > $gstol)" >&2
                                fail=1
                            fi
                        fi
                    fi
                done
            done
        fi
    done
fi

# Stratifier assertions (StratifierEvaluator regression lock). Each entry asserts a
# strataId, plus per-stratum populations + score. Strata are matched by strataValue
# (jq lookup); order in the response doesn't matter.
expected_strats_count=$(jq -r '.stratifiers // [] | length' "$EXPECTED" | tr -d '\r')
if [ "$expected_strats_count" -gt 0 ] 2>/dev/null; then
    for i in $(seq 0 $((expected_strats_count - 1))); do
        strata_id=$(jq -r ".stratifiers[$i].strataId" "$EXPECTED" | tr -d '\r')
        strat_tolerance=$(jq -r ".stratifiers[$i].scoreTolerance // 0.5" "$EXPECTED" | tr -d '\r')
        actual_strats_for_id=$(echo "$RESPONSE" | jq -c ".groups[0].stratifiers[]? | select(.strataId == \"$strata_id\")" 2>/dev/null || echo "")
        if [ -z "$actual_strats_for_id" ]; then
            echo "    ✗ stratifier $strata_id: no strata in response" >&2
            fail=1
            continue
        fi
        expected_strata_count=$(jq -r ".stratifiers[$i].expectedStrata | length" "$EXPECTED")
        for j in $(seq 0 $((expected_strata_count - 1))); do
            sv=$(jq -r ".stratifiers[$i].expectedStrata[$j].strataValue" "$EXPECTED" | tr -d '\r')
            actual_stratum=$(echo "$actual_strats_for_id" | jq -c "select(.strataValue == \"$sv\")" | head -1)
            if [ -z "$actual_stratum" ]; then
                echo "    ✗ stratifier $strata_id stratum '$sv': not found in response" >&2
                fail=1
                continue
            fi
            # Per-stratum populations
            while IFS= read -r pk; do
                [ -z "$pk" ] && continue
                exp_pc=$(jq -r ".stratifiers[$i].expectedStrata[$j].populations[\"$pk\"]" "$EXPECTED" | tr -d '\r')
                act_pc=$(echo "$actual_stratum" | jq -r ".populations[]? | select(.populationType == \"$pk\") | .count // empty" | tr -d '\r' | head -1)
                if [ "${act_pc:-<null>}" = "$exp_pc" ]; then
                    echo "    ✓ stratifier $strata_id[$sv].$pk: $act_pc"
                else
                    echo "    ✗ stratifier $strata_id[$sv].$pk: got ${act_pc:-<null>}, expected $exp_pc" >&2
                    fail=1
                fi
            done < <(jq -r ".stratifiers[$i].expectedStrata[$j].populations | keys[]?" "$EXPECTED" | tr -d '\r')
            # Per-stratum score (optional)
            exp_score=$(jq -r ".stratifiers[$i].expectedStrata[$j].score // empty" "$EXPECTED" | tr -d '\r')
            if [ -n "$exp_score" ]; then
                act_score=$(echo "$actual_stratum" | jq -r '.measureScore // empty')
                if [ -z "$act_score" ] || [ "$act_score" = "null" ]; then
                    echo "    ✗ stratifier $strata_id[$sv].score: expected $exp_score, got null" >&2
                    fail=1
                else
                    sd=$(awk -v a="$act_score" -v b="$exp_score" 'BEGIN{d=a-b; if (d<0) d=-d; print d}')
                    sw=$(awk -v d="$sd" -v t="$strat_tolerance" 'BEGIN{print (d<=t)?1:0}')
                    if [ "$sw" = "1" ]; then
                        echo "    ✓ stratifier $strata_id[$sv].score: $act_score (Δ=$sd)"
                    else
                        echo "    ✗ stratifier $strata_id[$sv].score: $act_score (expected $exp_score, Δ=$sd > $strat_tolerance)" >&2
                        fail=1
                    fi
                fi
            fi
        done
    done
fi

# Supplemental Data defines presence — fetch the published CQL and grep. Light-touch:
# proves the CqlBuilder ran the SDE branch + translator accepted it. Runtime SDE values
# aren't reliably surfaced in the summary response, so we don't try to assert them.
sde_count=$(jq -r '.supplementalDataDefinesPresent // [] | length' "$EXPECTED" | tr -d '\r')
if [ "$sde_count" -gt 0 ] 2>/dev/null; then
    if [ -z "$MEASURE_ID" ]; then
        echo "    ✗ supplementalDataDefinesPresent set but measureId not passed to assert.sh" >&2
        fail=1
    else
        API_BASE="${API_BASE:-http://localhost:8080/api}"
        cql_text=$(curl -sf \
            -H "Authorization: Bearer ${TOKEN:-}" \
            "$API_BASE/measures/$MEASURE_ID/export/cql" 2>&1) || {
            echo "    ✗ SDE check: GET /measures/$MEASURE_ID/export/cql failed: $cql_text" >&2
            fail=1
            cql_text=""
        }
        if [ -n "$cql_text" ]; then
            for k in $(seq 0 $((sde_count - 1))); do
                sde_name=$(jq -r ".supplementalDataDefinesPresent[$k]" "$EXPECTED" | tr -d '\r')
                if echo "$cql_text" | grep -qF "define \"$sde_name\":"; then
                    echo "    ✓ SDE define present: $sde_name"
                else
                    echo "    ✗ SDE define missing in CQL: $sde_name" >&2
                    fail=1
                fi
            done
        fi
    fi
fi

# Idempotency check (PAT-???). Re-runs $evaluate-measure with the SAME measureId +
# period and asserts identical score + populations. Catches non-determinism (cache
# pollution, ordering bugs in MeasureReportBackfillService duplicate inserts, etc.).
check_idempotent=$(jq -r '.idempotent // false' "$EXPECTED" | tr -d '\r')
if [ "$check_idempotent" = "true" ]; then
    if [ -z "$MEASURE_ID" ]; then
        echo "    ✗ idempotent set but measureId not passed to assert.sh" >&2
        fail=1
    else
        API_BASE="${API_BASE:-http://localhost:8080/api}"
        period_start=$(jq -r '.periodStart' "$EXPECTED")
        period_end=$(jq -r '.periodEnd' "$EXPECTED")
        rerun=$(curl -sf -X POST \
            "$API_BASE/measures/$MEASURE_ID/\$evaluate-measure?periodStart=$period_start&periodEnd=$period_end&reportType=summary" \
            -H "Authorization: Bearer ${TOKEN:-}" 2>&1) || {
            echo "    ✗ idempotent re-run failed: $rerun" >&2
            fail=1
            rerun=""
        }
        if [ -n "$rerun" ]; then
            first_score=$(echo "$RESPONSE" | jq -r '.groups[0].measureScore // empty')
            second_score=$(echo "$rerun" | jq -r '.groups[0].measureScore // empty')
            if [ "$first_score" = "$second_score" ]; then
                echo "    ✓ idempotent score: $first_score (re-run identical)"
            else
                echo "    ✗ idempotent score drift: first=$first_score second=$second_score" >&2
                fail=1
            fi
            # Compare population counts as a JSON-sorted blob — order-insensitive equality.
            first_pops=$(echo "$RESPONSE" | jq -S '.groups[0].populations | map({populationType, count})')
            second_pops=$(echo "$rerun"   | jq -S '.groups[0].populations | map({populationType, count})')
            if [ "$first_pops" = "$second_pops" ]; then
                echo "    ✓ idempotent populations: identical"
            else
                echo "    ✗ idempotent populations differ between runs:" >&2
                diff <(echo "$first_pops") <(echo "$second_pops") >&2 || true
                fail=1
            fi
        fi
    fi
fi

# CSV export injection check. After evaluate, fetch the latest report and pull a
# CSV export. Asserts no row begins with a formula trigger character (=, +, -, @,
# \t, \r) — locks the CsvUtils.escapeCsv contract end-to-end including the
# MeasureReportExportService rendering path.
check_csv_export=$(jq -r '.checkCsvExport // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$check_csv_export" ] && [ "$check_csv_export" != "null" ]; then
    if [ -z "$MEASURE_ID" ]; then
        echo "    ✗ checkCsvExport set but measureId not passed to assert.sh" >&2
        fail=1
    else
        API_BASE="${API_BASE:-http://localhost:8080/api}"
        export_format=$(jq -r '.checkCsvExport.format // "csv"' "$EXPECTED" | tr -d '\r')
        # Find the latest report ID
        reports_resp=$(curl -sf -H "Authorization: Bearer ${TOKEN:-}" \
            "$API_BASE/measures/$MEASURE_ID/reports" 2>&1) || {
            echo "    ✗ checkCsvExport: GET /measures/$MEASURE_ID/reports failed: $reports_resp" >&2
            fail=1
            reports_resp=""
        }
        report_id=$(echo "$reports_resp" | jq -r '.[0].id // empty' 2>/dev/null | tr -d '\r')
        if [ -z "$report_id" ] || [ "$report_id" = "null" ]; then
            echo "    ✗ checkCsvExport: no reports for measure $MEASURE_ID" >&2
            fail=1
        else
            csv_body=$(curl -sf -H "Authorization: Bearer ${TOKEN:-}" \
                "$API_BASE/measures/reports/$report_id/export?format=$export_format" 2>&1) || {
                echo "    ✗ checkCsvExport: export $export_format failed: $csv_body" >&2
                fail=1
                csv_body=""
            }
            if [ -n "$csv_body" ]; then
                # Walk every non-empty line; assert no formula-trigger first char.
                bad_lines=""
                while IFS= read -r line; do
                    [ -z "$line" ] && continue
                    first_char="${line:0:1}"
                    case "$first_char" in
                        '='|'+'|'-'|'@'|$'\t'|$'\r')
                            bad_lines="${bad_lines}${line}"$'\n'
                            ;;
                    esac
                done <<< "$csv_body"
                if [ -z "$bad_lines" ]; then
                    line_count=$(printf '%s\n' "$csv_body" | wc -l | tr -d ' \r')
                    echo "    ✓ CSV export injection-safe: $line_count lines, none start with =/+/-/@/\\t/\\r"
                else
                    echo "    ✗ CSV export has formula-injection-prone line(s):" >&2
                    echo "$bad_lines" | head -3 | sed 's/^/      /' >&2
                    fail=1
                fi
            fi
        fi
    fi
fi

# Provenance check (PAT-095 regression lock). Fetches reports for the measure
# and validates the latest row has non-null measureVersion + cqlHash + elmHash.
# Requires MEASURE_ID passed as 3rd arg.
check_provenance=$(jq -r '.checkProvenance // false' "$EXPECTED" | tr -d '\r')
if [ "$check_provenance" = "true" ]; then
    if [ -z "$MEASURE_ID" ]; then
        echo "    ✗ checkProvenance set but measureId not passed to assert.sh" >&2
        fail=1
    else
        API_BASE="${API_BASE:-http://localhost:8080/api}"
        reports_response=$(curl -sf \
            -H "Authorization: Bearer ${TOKEN:-}" \
            "$API_BASE/measures/$MEASURE_ID/reports" 2>&1) || {
            echo "    ✗ checkProvenance: GET /measures/$MEASURE_ID/reports failed: $reports_response" >&2
            fail=1
            exit $fail
        }
        # Take the first element (most recent — repo uses findByMeasureDefinitionIdOrderByCreatedAtDesc).
        cql_hash=$(echo "$reports_response" | jq -r '.[0].cqlHash // empty' | tr -d '\r')
        elm_hash=$(echo "$reports_response" | jq -r '.[0].elmHash // empty' | tr -d '\r')
        measure_version=$(echo "$reports_response" | jq -r '.[0].measureVersion // empty' | tr -d '\r')
        if [ -z "$cql_hash" ] || [ "$cql_hash" = "null" ]; then
            echo "    ✗ provenance.cqlHash: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.cqlHash: ${cql_hash:0:16}…"
        fi
        if [ -z "$elm_hash" ] || [ "$elm_hash" = "null" ]; then
            echo "    ✗ provenance.elmHash: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.elmHash: ${elm_hash:0:16}…"
        fi
        if [ -z "$measure_version" ] || [ "$measure_version" = "null" ]; then
            echo "    ✗ provenance.measureVersion: missing on latest report" >&2
            fail=1
        else
            echo "    ✓ provenance.measureVersion: $measure_version"
        fi
    fi
fi

# Performance budget. run.sh sets EVAL_ELAPSED_MS as wall-clock around the
# evaluate.sh call; assert here flags scenarios that breach the per-scenario
# budget. Catches N+1 query regressions and bulk-fetch slowdowns that don't
# affect correctness but bloat prod cost. Budget is intentionally generous
# (typical prod scenarios run in 1-2s) — this guards against 10x regressions,
# not microbenchmarks.
max_eval_ms=$(jq -r '.maxEvaluationTimeMs // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$max_eval_ms" ] && [ -n "${EVAL_ELAPSED_MS:-}" ]; then
    if [ "$EVAL_ELAPSED_MS" -le "$max_eval_ms" ]; then
        echo "    ✓ evaluation within budget: ${EVAL_ELAPSED_MS}ms (≤ ${max_eval_ms}ms)"
    else
        echo "    ✗ evaluation slower than budget: ${EVAL_ELAPSED_MS}ms > ${max_eval_ms}ms" >&2
        fail=1
    fi
fi

exit $fail
