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

# Order matters: HAPI enforces referential integrity on DELETE (with
# enforce_referential_integrity_on_delete=true which is HAPI's default). Resources
# that reference Patient (Encounter, Observation, etc.) MUST be deleted first;
# otherwise the Patient DELETE fails with a constraint violation.
# Patient goes last so nothing is pointing at it by the time we try to remove it.
RESOURCE_TYPES=(Encounter Observation Condition Procedure MedicationRequest MedicationStatement AllergyIntolerance Patient)

for rtype in "${RESOURCE_TYPES[@]}"; do
    # `_lastUpdated=gt2000-01-01` is a predicate that matches everything — HAPI
    # rejects predicate-less conditional DELETE as too dangerous.
    # Drop -f so we see HAPI's response body on failure (referential integrity
    # violations report via HTTP 409 / 412 with an OperationOutcome body).
    response=$(curl -s -X DELETE "$FHIR_BASE/${rtype}?_lastUpdated=gt2000-01-01" \
        -H "Accept: application/fhir+json" \
        -w "\n__HTTP_STATUS__%{http_code}")
    http_status=$(echo "$response" | tail -1 | sed 's/__HTTP_STATUS__//')
    body=$(echo "$response" | sed '$d')
    # 200 = deleted something; 204 = nothing to delete (also OK)
    if [ "$http_status" != "200" ] && [ "$http_status" != "204" ]; then
        echo "conditional DELETE on $rtype failed with HTTP $http_status:" >&2
        echo "$body" >&2
        echo "Hint: hapi.fhir.allow_multiple_delete must be true (compose.override.yml);" >&2
        echo "  and RESOURCE_TYPES order must delete referring resources before referenced ones" >&2
        exit 1
    fi
done

echo "  reset FHIR — cleared ${#RESOURCE_TYPES[@]} resource types"
