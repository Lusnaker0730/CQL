#!/usr/bin/env bash
# PAT-229 — assertions for the `measure-package` scenario type. Looks at the
# files run.sh left in <dir>:
#   bundle.json / bundle.xml / conformance.json   from lib/export-package.sh
#   import-result.json                            from lib/import-package.sh
#   original.json / imported.json                 $evaluate-measure of both measures
#
# The population counts themselves are asserted by lib/assert.sh (run.sh feeds it
# imported.json); this script locks the PACKAGE: that it is the shape the HL7
# Quality Measure IG / CRMI describe, that a receiver gets the logic, and that
# the imported measure computes exactly what the exported one does.
#
# expected.json fields used here:
#   package.measureProfiles[]   profile URLs Measure.meta.profile must contain
#   package.libraries[]         Library.name values the bundle must contain (first = primary)
#   package.dependsOn[]         canonical-URL suffixes the primary Library must depend on
#   package.populationBasis     cqfm-populationBasis valueCode
#   package.improvementNotation improvementNotation code
#   package.standardMetadata    PAT-236 measureTypes / experimental / dates / clinicalRecommendationStatement / definitions[]
#   importAsVersion             version the round-tripped Measure is imported as
#   expectLibrariesSkipped      dependency libraries already stored here
#
# Usage:  lib/assert-measure-package.sh <dir> <expected.json>
set -euo pipefail

DIR="${1:?usage: assert-measure-package.sh <dir> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"

BUNDLE="$DIR/bundle.json"
fail=0

ok()  { echo "    ✓ $1"; }
bad() { echo "    ✗ $1" >&2; fail=1; }

# check <description> <jq filter that must output true> [file]
check() {
    local desc="$1" filter="$2" file="${3:-$BUNDLE}" out
    out=$(jq -r "$filter" "$file" 2>&1 | tr -d '\r') || true
    if [ "$out" = "true" ]; then ok "$desc"; else bad "$desc (jq → ${out:-<empty>})"; fi
}

QM_POPULATION_BASIS="http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-populationBasis"

# ── Bundle ──────────────────────────────────────────────────────────────────
check "bundle is a collection Bundle" '.resourceType == "Bundle" and .type == "collection"'
check "exactly one Measure" '[.entry[].resource | select(.resourceType == "Measure")] | length == 1'
check "every entry is addressed by its canonical url" \
    '[.entry[] | select(.resource.url != null) | .fullUrl == .resource.url] | all'

# ── Measure ─────────────────────────────────────────────────────────────────
M='(.entry[].resource | select(.resourceType == "Measure"))'
while IFS= read -r profile; do
    [ -z "$profile" ] && continue
    check "Measure claims ${profile##*/}" "[$M.meta.profile[]?] | index(\"$profile\") != null"
done < <(jq -r '.package.measureProfiles[]?' "$EXPECTED" | tr -d '\r')

check "Measure.url is an absolute canonical URL" "$M.url | test(\"^https?://.+/Measure/.+\")"
check "Measure has no dataRequirement (not an R4 Measure element)" "$M | has(\"dataRequirement\") | not"
check "Measure.library is exactly the primary Library's canonical|version" \
    "($M.library | length == 1) and ($M.library[0] == ([.entry[].resource | select(.resourceType == \"Library\")][0] | .url + \"|\" + .version))"
check "criteria are CQL identifiers" \
    "[$M.group[].population[].criteria.language] | all(. == \"text/cql-identifier\")"
check "every group and population has an id" \
    "([$M.group[] | .id] | all(. != null)) and ([$M.group[].population[] | .id] | all(. != null))"

basis=$(jq -r '.package.populationBasis // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$basis" ]; then
    check "cqfm-populationBasis = $basis" \
        "[$M | (.extension[]?, .group[].extension[]?) | select(.url == \"$QM_POPULATION_BASIS\") | .valueCode] | (length > 0 and all(. == \"$basis\"))"
fi
notation=$(jq -r '.package.improvementNotation // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$notation" ]; then
    check "improvementNotation = $notation" \
        "[$M | (.improvementNotation, .group[].extension[]?.valueCodeableConcept) | .coding[]?.code] | index(\"$notation\") != null"
fi

# ── Libraries ───────────────────────────────────────────────────────────────
expected_libs=$(jq -c '.package.libraries // []' "$EXPECTED" | tr -d '\r')
check "bundle carries the libraries $expected_libs" \
    "[.entry[].resource | select(.resourceType == \"Library\") | .name] == $expected_libs"
check "every Library ships its CQL" \
    '[.entry[].resource | select(.resourceType == "Library") | [.content[]?.contentType] | index("text/cql") != null] | all'
check "primary Library ships ELM too" \
    '[.entry[].resource | select(.resourceType == "Library")][0] | [.content[].contentType] | index("application/elm+json") != null'
while IFS= read -r dep; do
    [ -z "$dep" ] && continue
    check "primary Library depends-on …$dep" \
        "[.entry[].resource | select(.resourceType == \"Library\")][0] | [.relatedArtifact[]? | select(.type == \"depends-on\") | .resource] | any(endswith(\"$dep\"))"
done < <(jq -r '.package.dependsOn[]?' "$EXPECTED" | tr -d '\r')

# The CQL in the package must be the CQL that runs: decode it and look for the library header.
primary_name=$(jq -r '.package.libraries[0] // empty' "$EXPECTED" | tr -d '\r')
decoded=$(jq -r '[.entry[].resource | select(.resourceType == "Library")][0].content[] | select(.contentType == "text/cql") | .data' "$BUNDLE" \
    | tr -d '\r' | base64 -d 2>/dev/null || true)
if echo "$decoded" | grep -q "^library $primary_name "; then
    ok "packaged CQL decodes to 'library $primary_name …'"
else
    bad "packaged CQL does not start with 'library $primary_name' (got: $(echo "$decoded" | head -1))"
fi
# PAT-237: exact fragments the packaged primary CQL must contain (e.g. a library function call
# with its arguments, the include it needs, a parameter declaration).
while IFS= read -r fragment; do
    [ -z "$fragment" ] && continue
    if printf '%s' "$decoded" | grep -qF -- "$fragment"; then
        ok "packaged CQL contains: $fragment"
    else
        bad "packaged CQL lacks: $fragment"
    fi
done < <(jq -r '.package.cqlContains[]?' "$EXPECTED" | tr -d '\r')

# ── XML ─────────────────────────────────────────────────────────────────────
if grep -q '<Bundle xmlns="http://hl7.org/fhir">' "$DIR/bundle.xml" && grep -q '<Measure' "$DIR/bundle.xml" \
        && grep -q '<population' "$DIR/bundle.xml"; then
    ok "XML export is a FHIR XML Bundle with the Measure's populations"
else
    bad "XML export is not a full FHIR XML Bundle: $(head -c 300 "$DIR/bundle.xml")"
fi

# ── Conformance report ──────────────────────────────────────────────────────
check "conformance: exchangeReady" '.exchangeReady == true' "$DIR/conformance.json"
check "conformance: no error findings" '[.issues[] | select(.severity == "error")] | length == 0' "$DIR/conformance.json"
reported=$(jq -c '.profiles | sort' "$DIR/conformance.json" | tr -d '\r')
claimed=$(jq -c "[$M.meta.profile[]] | sort" "$BUNDLE" | tr -d '\r')
if [ -n "$reported" ] && [ "$reported" = "$claimed" ]; then
    ok "conformance reports exactly the profiles the Measure claims"
else
    bad "conformance profiles $reported ≠ Measure.meta.profile $claimed"
fi

# ── Import ──────────────────────────────────────────────────────────────────
import_version=$(jq -r '.importAsVersion' "$EXPECTED" | tr -d '\r')
check "imported measure starts as draft (the receiver approves it, not the sender)" \
    '.measure.status == "draft"' "$DIR/import-result.json"
check "imported as version $import_version" ".measure.version == \"$import_version\"" "$DIR/import-result.json"
check "imported measure HAS its logic" \
    ".measure.cqlContent != null and (.measure.cqlContent | test(\"library $primary_name \"))" "$DIR/import-result.json"
skipped=$(jq -r '.expectLibrariesSkipped // empty' "$EXPECTED" | tr -d '\r')
if [ -n "$skipped" ]; then
    check "dependency libraries already stored here are skipped ($skipped)" \
        ".librariesSkipped == $skipped and .librariesImported == 0" "$DIR/import-result.json"
fi

# ── PAT-236 standard metadata: on the exported Measure and back on the import ──
# expected.json `package.standardMetadata` (all optional): measureTypes[], experimental,
# effectiveStart, effectiveEnd, approvalDate, lastReviewDate, clinicalRecommendationStatement,
# definitions[] (the exact Measure.definition markdown entries).
if [ "$(jq -r '.package.standardMetadata != null' "$EXPECTED" | tr -d '\r')" = "true" ]; then
    SM='.package.standardMetadata'
    types=$(jq -c "$SM.measureTypes // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$types" ]; then
        check "Measure.type codes = $types" \
            "[$M.type[]?.coding[]? | select(.system == \"http://terminology.hl7.org/CodeSystem/measure-type\") | .code] == $types"
        check "import: measureTypes = $types" ".measure.measureTypes == $types" "$DIR/import-result.json"
    fi
    for field in experimental approvalDate lastReviewDate clinicalRecommendationStatement; do
        want=$(jq -c "$SM.$field // empty" "$EXPECTED" | tr -d '\r')
        [ -z "$want" ] && continue
        check "Measure.$field = $want" "$M.$field == $want"
        check "import: $field = $want" ".measure.$field == $want" "$DIR/import-result.json"
    done
    for bound in start end; do
        key=effectiveStart; [ "$bound" = "end" ] && key=effectiveEnd
        want=$(jq -c "$SM.$key // empty" "$EXPECTED" | tr -d '\r')
        [ -z "$want" ] && continue
        check "Measure.effectivePeriod.$bound = $want" "$M.effectivePeriod.$bound == $want"
        check "import: $key = $want" ".measure.$key == $want" "$DIR/import-result.json"
    done
    defs=$(jq -c "$SM.definitions // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$defs" ]; then
        check "Measure.definition = $defs" "$M.definition == $defs"
        check "import: definition terms parsed back ($(echo "$defs" | jq 'length') entries)" \
            "(.measure.definitionTerms | length) == $(echo "$defs" | jq 'length')" "$DIR/import-result.json"
    fi
fi

# ── Same package, same numbers ──────────────────────────────────────────────
summarise='[.groups[] | {score: .measureScore, populations: ([.populations[] | {(.populationType): .count}] | add)}]'
original=$(jq -c "$summarise" "$DIR/original.json" | tr -d '\r')
imported=$(jq -c "$summarise" "$DIR/imported.json" | tr -d '\r')
if [ -n "$original" ] && [ "$original" = "$imported" ]; then
    ok "imported measure evaluates to the same result as the exported one: $imported"
else
    bad "evaluation differs — exported: $original / imported: $imported"
fi

exit $fail
