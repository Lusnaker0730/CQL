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

# ── Exchange package zip + ELM XML (PAT-261) ───────────────────────────────
ZIP_DIR="$DIR/zip"
if [ -d "$ZIP_DIR" ]; then
    root_json=$(ls "$ZIP_DIR"/*-FHIR.json 2>/dev/null | head -1 || true)
    if [ -n "$root_json" ]; then
        ok "zip has a top-level <Title>-v<version>-FHIR.json ($(basename "$root_json"))"
        base="${root_json%.json}"
        zip_base=$(jq -r '.package.zipBaseName // empty' "$EXPECTED" | tr -d '\r')
        if [ -n "$zip_base" ]; then
            if [ "$(basename "$base")" = "$zip_base" ]; then ok "zip base name is $zip_base"; else bad "zip base name is $(basename "$base"), expected $zip_base"; fi
        fi
        if grep -q 'filename='"$(basename "$base")"'.zip' "$DIR/package.zip.headers" 2>/dev/null; then
            ok "Content-Disposition names the zip $(basename "$base").zip"
        else
            bad "Content-Disposition does not name the zip: $(grep -i disposition "$DIR/package.zip.headers" 2>/dev/null)"
        fi
        for ext in xml html; do
            if [ -f "$base.$ext" ]; then ok "zip has $(basename "$base").$ext"; else bad "zip lacks $(basename "$base").$ext"; fi
        done
        if grep -q '<Bundle xmlns="http://hl7.org/fhir">' "$base.xml" 2>/dev/null; then ok "zip XML is a FHIR XML Bundle"; else bad "zip XML is not a FHIR XML Bundle"; fi
        if grep -qi '<!DOCTYPE html>' "$base.html" 2>/dev/null && grep -q 'Population' "$base.html"; then ok "zip HTML is the human-readable document"; else bad "zip HTML is not the human-readable document"; fi
        check "zip Bundle JSON carries the same entries as the Bundle export" \
            "(.entry | length) == $(jq '.entry | length' "$BUNDLE" | tr -d '\r')" "$root_json"
    else
        bad "zip has no top-level *-FHIR.json: $(ls "$ZIP_DIR" | tr '\n' ' ')"
    fi
    if [ -f "$ZIP_DIR/conformance.json" ]; then ok "zip has conformance.json"; else bad "zip lacks conformance.json"; fi
    while IFS=$'\t' read -r lib_name lib_version; do
        [ -z "$lib_name" ] && continue
        if [ -f "$ZIP_DIR/cql/$lib_name-$lib_version.cql" ]; then ok "zip has cql/$lib_name-$lib_version.cql"; else bad "zip lacks cql/$lib_name-$lib_version.cql"; fi
        for ext in json xml; do
            if [ -f "$ZIP_DIR/resources/library-$lib_name-$lib_version.$ext" ]; then ok "zip has resources/library-$lib_name-$lib_version.$ext"; else bad "zip lacks resources/library-$lib_name-$lib_version.$ext"; fi
        done
    done < <(jq -r '.entry[].resource | select(.resourceType == "Library") | [.name, (.version // "1.0.0")] | @tsv' "$BUNDLE" | tr -d '\r')
    measure_name=$(jq -r "$M.name" "$BUNDLE" | tr -d '\r')
    measure_version=$(jq -r "$M.version // \"1.0.0\"" "$BUNDLE" | tr -d '\r')
    for ext in json xml; do
        if [ -f "$ZIP_DIR/resources/measure-$measure_name-$measure_version.$ext" ]; then ok "zip has resources/measure-$measure_name-$measure_version.$ext"; else bad "zip lacks resources/measure-$measure_name-$measure_version.$ext"; fi
    done
    if [ -n "$primary_name" ] && head -c 400 "$ZIP_DIR/cql/$primary_name"-*.cql 2>/dev/null | grep -q "library $primary_name"; then
        ok "zip cql/ file of the primary library is the CQL that runs"
    else
        bad "zip cql/ file of the primary library does not start with its library header"
    fi
else
    bad "package zip was not exported / unzipped into $ZIP_DIR"
fi
check "every Library ships ELM XML next to ELM JSON" \
    '[.entry[].resource | select(.resourceType == "Library") | [.content[]?.contentType] | (index("application/elm+json") != null) == (index("application/elm+xml") != null)] | all'
check "primary Library claims elm-xml-library" \
    '[.entry[].resource | select(.resourceType == "Library")][0].meta.profile | index("http://hl7.org/fhir/uv/cql/StructureDefinition/elm-xml-library") != null'
decoded_xml=$(jq -r '[.entry[].resource | select(.resourceType == "Library")][0].content[] | select(.contentType == "application/elm+xml") | .data' "$BUNDLE" \
    | tr -d '\r' | base64 -d 2>/dev/null | head -c 600 || true)
if printf '%s' "$decoded_xml" | grep -q '<library' && printf '%s' "$decoded_xml" | grep -q "id=\"$primary_name\""; then
    ok "primary Library's ELM XML is an ELM <library> for $primary_name"
else
    bad "primary Library's ELM XML is not an ELM library for $primary_name: $(printf '%s' "$decoded_xml" | head -c 160)"
fi
if [ -f "$DIR/elm.xml" ] && grep -q '<library' "$DIR/elm.xml" && grep -q "id=\"$primary_name\"" "$DIR/elm.xml"; then
    ok "GET /export/elm?format=xml is the ELM XML of $primary_name"
else
    bad "GET /export/elm?format=xml is not the ELM XML of $primary_name: $(head -c 200 "$DIR/elm.xml" 2>/dev/null)"
fi
if [ -f "$DIR/import-zip-result.json" ]; then
    zip_version="$(jq -r '.importAsVersion' "$EXPECTED" | tr -d '\r')-zip"
    check "zip import landed as version $zip_version (server-side asVersion)" ".measure.version == \"$zip_version\"" "$DIR/import-zip-result.json"
    check "zip import carries the CQL" '(.measure.cqlContent // "") | length > 0' "$DIR/import-zip-result.json"
    check "zip import skipped the shared library the JSON import had already stored" '.librariesSkipped >= 1' "$DIR/import-zip-result.json"
else
    bad "no import-zip-result.json: the zip was not imported"
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
    # PAT-256 fill-ins: short-name / endorser identifiers (THO artifact-identifier-type), Measure.endorser,
    # Measure.riskAdjustment and the QM IG cqfm-supplementalDataGuidance complex extension.
    SDG_EXT='http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/cqfm-supplementalDataGuidance'
    want=$(jq -c "$SM.ecqmTitle // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$want" ]; then
        check "Measure.identifier short-name = $want" \
            "[$M.identifier[]? | select(.type.coding[]?.code == \"short-name\") | .value] == [$want]"
        check "import: ecqmTitle = $want" ".measure.ecqmTitle == $want" "$DIR/import-result.json"
    fi
    want=$(jq -c "$SM.endorsementId // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$want" ]; then
        check "Measure.identifier endorser = $want" \
            "[$M.identifier[]? | select(.type.coding[]?.code == \"endorser\") | .value] == [$want]"
        check "import: endorsementId = $want" ".measure.endorsementId == $want" "$DIR/import-result.json"
    fi
    want=$(jq -c "$SM.endorser // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$want" ]; then
        check "Measure.endorser[0].name = $want" "$M.endorser[0].name == $want"
        check "import: endorser = $want" ".measure.endorser == $want" "$DIR/import-result.json"
    fi
    want=$(jq -c "$SM.riskAdjustmentDescription // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$want" ]; then
        check "Measure.riskAdjustment = $want" "$M.riskAdjustment == $want"
        check "import: riskAdjustmentDescription = $want" ".measure.riskAdjustmentDescription == $want" "$DIR/import-result.json"
    fi
    want=$(jq -c "$SM.supplementalDataGuidance // empty" "$EXPECTED" | tr -d '\r')
    if [ -n "$want" ]; then
        check "Measure supplemental data guidance extension = $want" \
            "[$M.extension[]? | select(.url == \"$SDG_EXT\") | .extension[]? | select(.url == \"guidance\") | .valueMarkdown] == [$want]"
        check "import: supplementalDataGuidance = $want" ".measure.supplementalDataGuidance == $want" "$DIR/import-result.json"
    fi
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
