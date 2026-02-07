#!/usr/bin/env bash
# Database restore script for CQL Platform
# Restores from a compressed pg_dump backup
#
# Usage: ./restore-db.sh <backup_file> [container_name]
# Example: ./restore-db.sh ../postgres-backup/cqlplatform_20240101_120000.sql.gz

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup_file> [container_name]"
    echo "Example: $0 ../postgres-backup/cqlplatform_20240101_120000.sql.gz"
    exit 1
fi

BACKUP_FILE="$1"
CONTAINER="${2:-docker-postgres-1}"

# Load env vars if available
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
if [ -f "$ENV_FILE" ]; then
    # shellcheck source=/dev/null
    source "$ENV_FILE"
fi

DB_USER="${POSTGRES_USER:-cqlplatform}"
DB_NAME="${POSTGRES_DB:-cqlplatform}"

# Validate backup file exists
if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

echo "WARNING: This will drop and recreate the '${DB_NAME}' database."
echo "Container: ${CONTAINER}"
echo "Backup file: ${BACKUP_FILE}"
echo ""
read -rp "Are you sure you want to continue? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Dropping and recreating database '${DB_NAME}'..."
docker exec -t "$CONTAINER" psql -U "$DB_USER" -d postgres -c "
    SELECT pg_terminate_backend(pg_stat_activity.pid)
    FROM pg_stat_activity
    WHERE pg_stat_activity.datname = '${DB_NAME}' AND pid <> pg_backend_pid();
"
docker exec -t "$CONTAINER" psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME};"
docker exec -t "$CONTAINER" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

echo "Restoring from backup..."
gunzip -c "$BACKUP_FILE" | docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME"

echo "Restore completed successfully."
echo "You may need to restart the backend service: docker compose restart backend"
