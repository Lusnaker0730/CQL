#!/usr/bin/env bash
# PAT-230 — assertions for the `platform-value-set` scenario type that are not
# population counts (those go through lib/assert.sh, once per value set version).
# Looks at the files run.sh left in <dir>:
#   measure.json        GET /api/measures/{id}          (the generated CQL)
#   v1.json             GET /api/value-sets/{v1 id}     (after v2 was activated)
#   bundle.json         GET /api/measures/{id}/export/bundle
#   conformance.json    GET /api/measures/{id}/export/conformance
#   expand.json         GET /api/fhir/ValueSet/$expand?url=…
#   search.json         GET /api/fhir/ValueSet?title=…
#
# Usage:  lib/assert-platform-value-set.sh <dir> <expected.json>
set -euo pipefail

DIR="${1:?usage: assert-platform-value-set.sh <dir> <expected.json>}"
EXPECTED="${2:?expected.json path missing}"
fail=0

ok()  { echo "    ✓ $1"; }
bad() { echo "    ✗ $1" >&2; fail=1; }
check() {
    local desc="$1" filter="$2" file="$3" out
    out=$(jq -r "$filter" "$file" 2>&1 | tr -d '\r') || true
    if [ "$out" = "true" ]; then ok "$desc"; else bad "$desc (jq → ${out:-<empty>})"; fi
}

url=$(jq -r '.valueSet.url' "$EXPECTED" | tr -d '\r')
next_version=$(jq -r '.nextVersion.version' "$EXPECTED" | tr -d '\r')
next_codes=$(jq -c '[.nextVersion.concepts[].code] | sort' "$EXPECTED" | tr -d '\r')
first_codes=$(jq -c '[.valueSet.concepts[].code] | sort' "$EXPECTED" | tr -d '\r')

# The generator must declare the URL the author picked — it used to declare the NAME as the URL.
declaration=$(jq -r '.expectedCqlDeclaration' "$EXPECTED" | tr -d '\r')
if jq -r '.cqlContent // ""' "$DIR/measure.json" | tr -d '\r' | grep -qF "$declaration"; then
    ok "generated CQL declares: $declaration"
else
    bad "generated CQL lacks '$declaration' — got: $(jq -r '.cqlContent // ""' "$DIR/measure.json" | grep -i '^valueset' | head -3)"
fi

# Activating v2 must not have touched v1.
check "version 1 still holds exactly its own codes ($first_codes)" \
    "(.status == \"active\") and ([.concepts[].code] | sort) == $first_codes" "$DIR/v1.json"

# The pickers and the expansion preview.
check "\$expand answers the platform value set with the newest active version ($next_version)" \
    "(.url == \"$url\") and (.version == \"$next_version\") and ([.expansion.contains[].code] | sort) == $next_codes" "$DIR/expand.json"
check "value set search lists it first, marked as the platform's" \
    ".[0].url == \"$url\" and .[0].source == \"platform\" and .[0].status == \"active\"" "$DIR/search.json"

# The exchange package carries the codes, not just the name.
VS="(.entry[].resource | select(.resourceType == \"ValueSet\" and .url == \"$url\"))"
check "package embeds the value set in full, version $next_version" \
    "($VS.version == \"$next_version\") and ([$VS.compose.include[].concept[].code] | sort) == $next_codes" "$DIR/bundle.json"
check "conformance: packaged from the platform store" \
    "[.valueSets[] | select(.url == \"$url\")] | (length == 1 and .[0].included == true and .[0].source == \"platform\")" \
    "$DIR/conformance.json"
check "conformance: no 'could not be resolved' warning for it" \
    "[.issues[] | select(.message | contains(\"could not be resolved\"))] | length == 0" "$DIR/conformance.json"
check "conformance: notes that the reference is not pinned to a version" \
    "[.issues[] | select(.severity == \"info\" and (.message | contains(\"without a version\")))] | length == 1" "$DIR/conformance.json"

exit $fail
