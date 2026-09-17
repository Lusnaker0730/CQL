#!/usr/bin/env bash
# Database backup script for CQL Platform
# Creates a compressed pg_dump with 30-day retention
#
# Usage: ./backup-db.sh [container_name]
# Default container: docker-postgres-1

set -euo pipefail

CONTAINER="${1:-docker-postgres-1}"
BACKUP_DIR="$(cd "$(dirname "$0")/.." && pwd)/postgres-backup"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/cqlplatform_${TIMESTAMP}.sql.gz"
RETENTION_DAYS=30

# Load env vars if available
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
if [ -f "$ENV_FILE" ]; then
    # shellcheck source=/dev/null
    source "$ENV_FILE"
fi

DB_USER="${POSTGRES_USER:-cqlplatform}"
DB_NAME="${POSTGRES_DB:-cqlplatform}"

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR"

echo "Starting backup of database '${DB_NAME}'..."
echo "Container: ${CONTAINER}"
echo "Output: ${BACKUP_FILE}"

# Run pg_dump inside the container and pipe compressed output
docker exec -t "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" --format=plain --no-owner --no-privileges | gzip > "$BACKUP_FILE"

# Verify backup was created and is not empty
if [ ! -s "$BACKUP_FILE" ]; then
    echo "ERROR: Backup file is empty or was not created"
    rm -f "$BACKUP_FILE"
    exit 1
fi

BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "Backup completed successfully: ${BACKUP_FILE} (${BACKUP_SIZE})"

# Export a freshness metric for Prometheus (via node-exporter's textfile collector).
# Powers the BackupStale / BackupMetricMissing alerts. Written atomically (tmp+mv)
# so node-exporter never scrapes a half-written file. Best-effort: a metrics-dir
# failure must never fail the backup itself.
TEXTFILE_DIR="$(cd "$(dirname "$0")/.." && pwd)/node-exporter-textfile"
BACKUP_BYTES=$(stat -c %s "$BACKUP_FILE" 2>/dev/null || wc -c < "$BACKUP_FILE")
if mkdir -p "$TEXTFILE_DIR" 2>/dev/null; then
    METRIC_FILE="${TEXTFILE_DIR}/cql_backup.prom"
    {
        echo "# HELP cql_backup_last_success_timestamp_seconds Unix time of the last successful DB backup"
        echo "# TYPE cql_backup_last_success_timestamp_seconds gauge"
        echo "cql_backup_last_success_timestamp_seconds $(date +%s)"
        echo "# HELP cql_backup_size_bytes Size in bytes of the last successful DB backup"
        echo "# TYPE cql_backup_size_bytes gauge"
        echo "cql_backup_size_bytes ${BACKUP_BYTES}"
    } > "${METRIC_FILE}.tmp" && mv -f "${METRIC_FILE}.tmp" "${METRIC_FILE}"
    echo "Wrote freshness metric: ${METRIC_FILE}"
else
    echo "WARN: could not write freshness metric dir ${TEXTFILE_DIR} (non-fatal)" >&2
fi

# Cleanup old backups
echo "Removing backups older than ${RETENTION_DAYS} days..."
DELETED=$(find "$BACKUP_DIR" -name "cqlplatform_*.sql.gz" -mtime +${RETENTION_DAYS} -delete -print | wc -l)
echo "Deleted ${DELETED} old backup(s)"

echo "Done."
