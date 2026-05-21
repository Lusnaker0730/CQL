#!/usr/bin/env bash
# Poll a URL until it returns HTTP 200, or give up after TIMEOUT seconds.
# Used to wait for backend / HAPI FHIR to finish booting before the first API call.
set -euo pipefail

URL="${1:?usage: wait-health.sh <url> [timeout_seconds]}"
TIMEOUT="${2:-90}"

deadline=$(( $(date +%s) + TIMEOUT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
    if curl -sf -o /dev/null -w "%{http_code}" "$URL" 2>/dev/null | grep -q "^200$"; then
        echo "  $URL — ready"
        exit 0
    fi
    sleep 2
done

echo "  $URL — still not healthy after ${TIMEOUT}s" >&2
exit 1
