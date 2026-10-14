#!/usr/bin/env bash
# PAT-260: assert the files run.sh collected for the terminology-pin scenario.
#
# Usage:  lib/assert-terminology-pin.sh <dir> <expected.json> <pinnedMeasureId> <floatingMeasureId>
set -u
DIR="${1:?usage: assert-terminology-pin.sh <dir> <expected.json> <pinnedId> <floatingId>}"
EXPECTED="${2:?expected.json missing}"
PINNED_ID="${3:?pinned id missing}"
FLOATING_ID="${4:?floating id missing}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fail=0
check() {   # check <label> <jq boolean expression> <file>
    local label="$1" expr="$2" file="$3"
    if [ "$(jq -r "$expr" "$file" 2>/dev/null | tr -d '\r')" = "true" ]; then
        echo "    ✓ $label" >&2
    else
        echo "    ✗ $label — got: $(head -c 300 "$file" | tr -d '\n')" >&2
        fail=1
    fi
}
url=$(jq -r '.valueSet.url' "$EXPECTED" | tr -d '\r')
v1=$(jq -r '.valueSet.version' "$EXPECTED" | tr -d '\r')
v2=$(jq -r '.nextVersion.version' "$EXPECTED" | tr -d '\r')
item="(.items[] | select(.url == \"$url\"))"

check "before pinning: the platform value set resolves to $v1 and floats" \
    ".pinnable == true and ($item | .source == \"platform\" and .resolvedVersion == \"$v1\" and .pinned == false and .pinnedVersion == null)" "$DIR/terminology-before.json"
check "after POST /terminology/pin: pinned to $v1" \
    "($item | .pinned == true and .pinnedVersion == \"$v1\")" "$DIR/terminology-pinned.json"
check "floating measure: approval readiness warns TERMINOLOGY_UNPINNED but is ready" \
    ".ready == true and ([.warnings[]?.code] | index(\"TERMINOLOGY_UNPINNED\") != null)" "$DIR/readiness-floating.json"

jq '.pinned' "$EXPECTED" > "$DIR/expected-pinned.json"
jq '.floating.before' "$EXPECTED" > "$DIR/expected-floating-before.json"
jq '.floating.after' "$EXPECTED" > "$DIR/expected-floating-after.json"
echo "    — pinned measure, value set $v1 active:" >&2
bash "$SCRIPT_DIR/assert.sh" "$DIR/pinned-v1.json" "$DIR/expected-pinned.json" "$PINNED_ID" || fail=1
echo "    — floating measure, value set $v1 active:" >&2
bash "$SCRIPT_DIR/assert.sh" "$DIR/floating-v1.json" "$DIR/expected-floating-before.json" "$FLOATING_ID" || fail=1
echo "    — pinned measure after $v2 was activated (must not move):" >&2
bash "$SCRIPT_DIR/assert.sh" "$DIR/pinned-v2.json" "$DIR/expected-pinned.json" "$PINNED_ID" || fail=1
echo "    — floating measure after $v2 was activated (follows the newest active):" >&2
bash "$SCRIPT_DIR/assert.sh" "$DIR/floating-v2.json" "$DIR/expected-floating-after.json" "$FLOATING_ID" || fail=1

for f in pinned-v1 floating-v1 pinned-v2; do
    check "$f: terminologyVersions names $v1" ".terminologyVersions[\"$url\"] == \"$v1\"" "$DIR/$f.json"
done
check "floating-v2: terminologyVersions names $v2" ".terminologyVersions[\"$url\"] == \"$v2\"" "$DIR/floating-v2.json"
check "exchange package: the primary Library depends on $url|$v1" \
    "[.entry[].resource | select(.resourceType == \"Library\") | .relatedArtifact[]? | select(.type == \"depends-on\") | .resource] | index(\"$url|$v1\") != null" "$DIR/bundle.json"
exit $fail
