#!/usr/bin/env bash
# Log in via /api/auth/login using the seeded admin creds from DataInitializer.
# Emits ONLY the JWT to stdout on success (one line, no trailing newline). On
# failure, prints diagnostic to stderr and exits 1.
#
# Caller does:  TOKEN=$(lib/auth.sh)
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080/api}"

response=$(curl -sf -X POST "$API_BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}' 2>&1) || {
    echo "login failed: $response" >&2
    exit 1
}

token=$(echo "$response" | jq -r '.token // empty')
if [ -z "$token" ]; then
    echo "login response had no .token: $response" >&2
    exit 1
fi

printf '%s' "$token"
