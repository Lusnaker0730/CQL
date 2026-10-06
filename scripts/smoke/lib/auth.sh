#!/usr/bin/env bash
# Log in via /api/auth/login. Defaults to the seeded admin creds from DataInitializer;
# SMOKE_USER / SMOKE_PASSWORD pick another seeded account (PAT-249: the `demo` user acts as
# the second pair of eyes that approves what admin submitted).
# Emits ONLY the JWT to stdout on success (one line, no trailing newline). On
# failure, prints diagnostic to stderr and exits 1.
#
# Caller does:  TOKEN=$(lib/auth.sh)
#               REVIEWER_TOKEN=$(SMOKE_USER=demo SMOKE_PASSWORD=password lib/auth.sh)
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080/api}"
USERNAME="${SMOKE_USER:-admin}"
PASSWORD="${SMOKE_PASSWORD:-admin}"

raw=$(curl -s -X POST "$API_BASE/auth/login" \
    -H "Content-Type: application/json" \
    -w "\n__HTTP_STATUS__%{http_code}" \
    -d "$(jq -nc --arg u "$USERNAME" --arg p "$PASSWORD" '{username: $u, password: $p}')") || {
    echo "login transport error for $USERNAME (curl failed)" >&2
    exit 1
}
http_status=$(echo "$raw" | tail -1 | sed 's/__HTTP_STATUS__//')
response=$(echo "$raw" | sed '$d')
if [ "$http_status" != "200" ]; then
    echo "login failed for $USERNAME: HTTP $http_status $(echo "$response" | head -c 300)" >&2
    exit 1
fi

token=$(echo "$response" | jq -r '.token // empty')
if [ -z "$token" ]; then
    echo "login response had no .token: $response" >&2
    exit 1
fi

printf '%s' "$token"
