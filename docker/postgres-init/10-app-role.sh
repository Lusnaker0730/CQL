#!/bin/bash
# PAT-223 — create the least-privilege application role that makes PostgreSQL
# Row-Level Security real.
#
# POSTGRES_USER (the role the official image creates) is a SUPERUSER, and a
# superuser bypasses every RLS policy. So the backend must connect as a separate
# NOSUPERUSER / NOBYPASSRLS role (DB_APP_USERNAME / DB_APP_PASSWORD) while Flyway
# keeps using the owner (DB_USERNAME / DB_PASSWORD) for migrations.
#
# Runs automatically ONCE, on the first init of a fresh data volume
# (/docker-entrypoint-initdb.d). For an EXISTING database (production VM) run it
# by hand — it is idempotent:
#
#   cd /opt/CQL/docker
#   docker compose exec -e DB_APP_USERNAME -e DB_APP_PASSWORD postgres \
#       bash /docker-entrypoint-initdb.d/10-app-role.sh
#
# then set DB_APP_USERNAME / DB_APP_PASSWORD in docker/.env and restart the
# backend. TenantRlsStartupCheck logs "Tenant RLS ENFORCED" when it worked.
set -euo pipefail

APP_ROLE="${DB_APP_USERNAME:-}"
APP_PW="${DB_APP_PASSWORD:-}"

if [ -z "$APP_ROLE" ] || [ "$APP_ROLE" = "${POSTGRES_USER}" ]; then
    echo "[app-role] DB_APP_USERNAME not set (or equal to POSTGRES_USER) — skipping." \
         "The backend will run as the owner role and RLS will NOT be enforced."
    exit 0
fi
if [ -z "$APP_PW" ]; then
    echo "[app-role] DB_APP_PASSWORD is required when DB_APP_USERNAME is set" >&2
    exit 1
fi

# Single quotes inside the password must be doubled for the SQL literal.
pw_literal="${APP_PW//\'/\'\'}"

exists=$(psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "SELECT 1 FROM pg_roles WHERE rolname = '${APP_ROLE//\'/\'\'}'")

if [ "$exists" = "1" ]; then
    echo "[app-role] role '$APP_ROLE' exists — refreshing password and RLS attributes"
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        -c "ALTER ROLE \"$APP_ROLE\" WITH LOGIN PASSWORD '$pw_literal' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS"
else
    echo "[app-role] creating role '$APP_ROLE' (NOSUPERUSER NOBYPASSRLS)"
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
        -c "CREATE ROLE \"$APP_ROLE\" LOGIN PASSWORD '$pw_literal' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS"
fi

# Data access on everything that exists now, plus default privileges so tables and
# sequences that future Flyway migrations create (as POSTGRES_USER) are covered too.
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<EOSQL
GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO "$APP_ROLE";
GRANT USAGE ON SCHEMA public TO "$APP_ROLE";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "$APP_ROLE";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO "$APP_ROLE";
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "$APP_ROLE";
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO "$APP_ROLE";
EOSQL

echo "[app-role] done — role '$APP_ROLE' ready; set DB_APP_USERNAME / DB_APP_PASSWORD for the backend"
