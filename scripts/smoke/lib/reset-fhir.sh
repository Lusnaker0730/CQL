#!/usr/bin/env bash
# Wipe all FHIR resources from HAPI between scenarios. Conditional DELETE by
# _lastUpdated covers every resource ever created in this stack — HAPI with
# `allow_multiple_delete=true` (set in compose.override.yml) accepts type-level
# DELETE with a search predicate.
#
# Why not $expunge: on HAPI, `$expunge?_expungeEverything=true` only purges
# already-deleted resources and old versions. Live resources still show in
# search. So we DELETE first, then optionally $expunge to drop the soft-deleted
# rows — smoke doesn't bother with the second step because searches already
# ignore the tombstones.
#
# Why these resource types: covers every FHIR type we currently seed across
# scenarios. Adding a new type to a scenario's bundle? Add it here too or
# cross-scenario pollution comes back.
set -euo pipefail

FHIR_BASE="${FHIR_BASE:-http://localhost:8081/fhir}"

RESOURCE_TYPES=(Patient Encounter Observation Condition Procedure MedicationRequest MedicationStatement AllergyIntolerance)

deleted_any=0
for rtype in "${RESOURCE_TYPES[@]}"; do
    # `_lastUpdated=gt2000-01-01` is a predicate that matches everything — HAPI
    # rejects predicate-less conditional DELETE as too dangerous.
    response=$(curl -sf -X DELETE "$FHIR_BASE/${rtype}?_lastUpdated=gt2000-01-01" \
        -H "Accept: application/fhir+json" 2>&1) || {
        # Only fail hard for actual errors; "nothing to delete" responds 200 with an OO,
        # so a curl failure here means auth/network/config broke.
        echo "conditional DELETE on $rtype failed: $response" >&2
        echo "Hint: hapi.fhir.allow_multiple_delete must be true (compose.override.yml)" >&2
        exit 1
    }
    deleted_any=1
done

echo "  reset FHIR — cleared ${#RESOURCE_TYPES[@]} resource types"
