# Backup & Recovery Documentation

## CQL Platform — Backup Strategy, Disaster Recovery, and Business Continuity

**Document Version**: 1.0
**Last Updated**: 2026-02-22
**Classification**: Confidential
**Owner**: CQL Platform Operations Team

---

## Table of Contents

1. [Overview](#1-overview)
2. [Recovery Objectives](#2-recovery-objectives)
3. [Data Classification & Inventory](#3-data-classification--inventory)
4. [Backup Strategy](#4-backup-strategy)
5. [Backup Procedures](#5-backup-procedures)
6. [Recovery Procedures](#6-recovery-procedures)
7. [Disaster Recovery Plan](#7-disaster-recovery-plan)
8. [Testing & Validation](#8-testing--validation)
9. [Monitoring & Alerting](#9-monitoring--alerting)
10. [Roles & Responsibilities](#10-roles--responsibilities)

---

## 1. Overview

This document defines the backup strategy, recovery procedures, and disaster recovery plan for the CQL Platform. It covers all persistent data stores and configuration required to restore the platform to operational status.

### Architecture Reference

```
┌─────────────────────────────────────────────────────────────┐
│                    CQL Platform Stack                        │
│                                                              │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Nginx    │  │ Backend      │  │ PostgreSQL             │ │
│  │ (Proxy)  │──│ (Spring Boot)│──│ (Primary Data Store)   │ │
│  └──────────┘  └──────────────┘  └────────────────────────┘ │
│                       │                     │                │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Grafana  │  │ HAPI FHIR    │  │ Prometheus             │ │
│  │ (Dashbd) │  │ (FHIR Store) │  │ (Metrics, 30d retain.) │ │
│  └──────────┘  └──────────────┘  └────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Data Stores Requiring Backup

| Store | Type | Data | Criticality |
|---|---|---|---|
| PostgreSQL | Relational DB | Users, measures, audit logs, test cases, configurations | **Critical** |
| HAPI FHIR | FHIR Server | Patient data, clinical resources (if local) | **High** |
| Docker Volumes | File storage | PostgreSQL data, Grafana dashboards, Prometheus metrics | **Critical** |
| Application Config | Environment vars | JWT secret, encryption key, database credentials | **Critical** |
| Flyway Migrations | SQL files | Database schema versions | **High** |

---

## 2. Recovery Objectives

### RPO (Recovery Point Objective)

| Tier | Data | RPO | Justification |
|---|---|---|---|
| Tier 1 | PostgreSQL (users, measures, audit) | **1 hour** | Core application data; WAL archiving enables point-in-time recovery |
| Tier 2 | HAPI FHIR data | **24 hours** | Typically sourced from external EHR; can be re-imported |
| Tier 3 | Prometheus metrics | **7 days** | Monitoring data; 30-day retention, non-critical |
| Tier 4 | Grafana dashboards | **30 days** | Configuration data; can be recreated from provisioning files |

### RTO (Recovery Time Objective)

| Scenario | RTO | Method |
|---|---|---|
| Single service failure | **5 minutes** | Docker auto-restart, health checks |
| Database corruption | **30 minutes** | Restore from latest backup + WAL replay |
| Full server failure | **2 hours** | New server + Docker Compose + backup restore |
| Complete disaster (all infrastructure) | **4 hours** | New infrastructure + full backup restore |
| Data center failure | **8 hours** | DR site activation + backup restore |

---

## 3. Data Classification & Inventory

### 3.1 Database Tables (PostgreSQL)

| Table | Records (Est.) | Sensitivity | Backup Priority |
|---|---|---|---|
| `app_user` | < 1,000 | **High** (PII: encrypted emails) | Critical |
| `measure_definition` | < 10,000 | Medium (business data) | Critical |
| `test_case` | < 50,000 | **High** (PHI in FHIR bundles) | Critical |
| `audit_log` | > 100,000 | Medium (access records) | High |
| `measure_audit` | < 50,000 | Medium (change history) | High |
| `cds_service_config` | < 100 | Low (configuration) | High |
| `cds_artifact` | < 1,000 | Medium (CDS artifacts) | High |
| `ehr_connection` | < 100 | **High** (connection credentials) | Critical |
| `patient_import_record` | < 10,000 | **High** (import metadata) | High |
| `notification` | < 100,000 | Low (UI notifications) | Medium |
| `department` | < 100 | Low (organizational) | High |
| `indicator_catalog` | < 1,000 | Low (reference data) | Medium |
| `measure_threshold` | < 1,000 | Low (configuration) | Medium |
| `measure_report` | < 50,000 | Medium (evaluation results) | High |
| `user_api_key` | < 1,000 | **High** (API keys) | Critical |
| `password_reset_token` | < 100 | **High** (reset tokens) | Medium |

### 3.2 Configuration & Secrets

| Item | Storage | Backup Method |
|---|---|---|
| JWT_SECRET | Environment variable | Secrets manager / encrypted vault |
| ENCRYPTION_KEY | Environment variable | Secrets manager / encrypted vault |
| DB_PASSWORD | Environment variable | Secrets manager / encrypted vault |
| OKTA_CLIENT_SECRET | Environment variable | Secrets manager / encrypted vault |
| `.env` file | Docker host filesystem | Encrypted file backup, separate from DB backup |
| `docker-compose.yml` | Git repository | Version controlled |
| `application.yml` | Git repository (no secrets) | Version controlled |
| Flyway migrations | Git repository | Version controlled |
| Nginx config | Git repository | Version controlled |

---

## 4. Backup Strategy

### 4.1 PostgreSQL Backup Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                PostgreSQL Backup Layers                       │
│                                                              │
│  Layer 1: WAL Archiving (Continuous)                         │
│  ├── Enables point-in-time recovery (PITR)                   │
│  ├── RPO: minutes (since last WAL segment)                   │
│  └── Config: -k flag in docker-compose (data checksums)      │
│                                                              │
│  Layer 2: Automated pg_dump (Daily)                          │
│  ├── Full logical backup                                     │
│  ├── RPO: 24 hours (worst case without WAL)                  │
│  └── Retention: 30 days                                      │
│                                                              │
│  Layer 3: Docker Volume Snapshot (Weekly)                     │
│  ├── Physical backup of entire data directory                │
│  ├── Fastest full restore                                    │
│  └── Retention: 4 weeks                                      │
│                                                              │
│  Layer 4: Off-site Backup (Daily)                            │
│  ├── Encrypted pg_dump to remote storage                     │
│  ├── Geographic redundancy                                   │
│  └── Retention: 90 days                                      │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Backup Schedule

| Backup Type | Frequency | Time | Retention | Storage |
|---|---|---|---|---|
| WAL archiving | Continuous | — | 7 days | Local volume |
| pg_dump (logical) | Daily | 02:00 UTC | 30 days | Local + remote |
| Docker volume snapshot | Weekly | Sunday 03:00 UTC | 4 weeks | Local |
| Off-site encrypted backup | Daily | 04:00 UTC | 90 days | Remote/cloud storage |
| Configuration backup | On change | — | Indefinite | Git repository |
| Secrets backup | On change | — | Current + previous | Encrypted vault |

### 4.3 Backup Retention Policy

| Retention Tier | Period | Backup Type |
|---|---|---|
| Short-term | 7 days | WAL segments, hourly snapshots |
| Medium-term | 30 days | Daily pg_dump |
| Long-term | 90 days | Off-site encrypted backups |
| Audit compliance | 365 days | Audit log data (in-database retention) |

---

## 5. Backup Procedures

### 5.1 PostgreSQL Logical Backup (pg_dump)

```bash
#!/bin/bash
# backup-postgres.sh — Daily PostgreSQL backup script
# Schedule: crontab -e → 0 2 * * * /opt/cql-platform/scripts/backup-postgres.sh

set -euo pipefail

# Configuration
BACKUP_DIR="/opt/cql-platform/backups/postgres"
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/cqlplatform_${TIMESTAMP}.sql.gz"
CONTAINER_NAME="cql-postgres"

# Ensure backup directory exists
mkdir -p "${BACKUP_DIR}"

# Create compressed backup
docker exec "${CONTAINER_NAME}" pg_dump \
  -U "${POSTGRES_USER:-cqlplatform}" \
  -d "${POSTGRES_DB:-cqlplatform}" \
  --format=custom \
  --compress=9 \
  --verbose \
  > "${BACKUP_FILE}" 2>"${BACKUP_DIR}/backup_${TIMESTAMP}.log"

# Verify backup
if [ -s "${BACKUP_FILE}" ]; then
  echo "[$(date)] Backup successful: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

  # Calculate checksum
  sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"

  # Clean up old backups
  find "${BACKUP_DIR}" -name "cqlplatform_*.sql.gz" -mtime +${RETENTION_DAYS} -delete
  find "${BACKUP_DIR}" -name "cqlplatform_*.sha256" -mtime +${RETENTION_DAYS} -delete
  find "${BACKUP_DIR}" -name "backup_*.log" -mtime +${RETENTION_DAYS} -delete
else
  echo "[$(date)] ERROR: Backup failed or empty!" >&2
  # Alert operations team
  exit 1
fi
```

### 5.2 Docker Volume Backup

```bash
#!/bin/bash
# backup-volumes.sh — Weekly Docker volume backup
# Schedule: 0 3 * * 0 /opt/cql-platform/scripts/backup-volumes.sh

set -euo pipefail

BACKUP_DIR="/opt/cql-platform/backups/volumes"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "${BACKUP_DIR}"

# Stop services for consistent snapshot (optional: use --read-only mode instead)
echo "[$(date)] Stopping services for volume backup..."
cd /opt/cql-platform/docker
docker compose stop backend

# Backup PostgreSQL data volume
docker run --rm \
  -v cql-postgres-data:/data:ro \
  -v "${BACKUP_DIR}":/backup \
  alpine tar czf "/backup/postgres-data_${TIMESTAMP}.tar.gz" -C /data .

# Backup HAPI FHIR data volume
docker run --rm \
  -v cql-hapi-data:/data:ro \
  -v "${BACKUP_DIR}":/backup \
  alpine tar czf "/backup/hapi-data_${TIMESTAMP}.tar.gz" -C /data .

# Backup Grafana data volume
docker run --rm \
  -v cql-grafana-data:/data:ro \
  -v "${BACKUP_DIR}":/backup \
  alpine tar czf "/backup/grafana-data_${TIMESTAMP}.tar.gz" -C /data .

# Restart services
docker compose start backend
echo "[$(date)] Services restarted."

# Cleanup: keep 4 weeks
find "${BACKUP_DIR}" -name "*.tar.gz" -mtime +28 -delete

echo "[$(date)] Volume backup complete."
```

### 5.3 Off-site Encrypted Backup

```bash
#!/bin/bash
# backup-offsite.sh — Encrypted off-site backup
# Requires: gpg key for backup encryption, rclone or aws cli for remote transfer

set -euo pipefail

BACKUP_DIR="/opt/cql-platform/backups/postgres"
OFFSITE_DIR="/opt/cql-platform/backups/offsite"
LATEST_BACKUP=$(ls -t "${BACKUP_DIR}"/cqlplatform_*.sql.gz | head -1)
GPG_RECIPIENT="cql-backup@your-domain.com"

mkdir -p "${OFFSITE_DIR}"

if [ -z "${LATEST_BACKUP}" ]; then
  echo "ERROR: No backup found to encrypt" >&2
  exit 1
fi

ENCRYPTED_FILE="${OFFSITE_DIR}/$(basename "${LATEST_BACKUP}").gpg"

# Encrypt backup
gpg --encrypt --recipient "${GPG_RECIPIENT}" --output "${ENCRYPTED_FILE}" "${LATEST_BACKUP}"

# Transfer to remote storage (example: S3)
# aws s3 cp "${ENCRYPTED_FILE}" s3://cql-backups/postgres/ --storage-class STANDARD_IA

# Transfer to remote storage (example: rclone)
# rclone copy "${ENCRYPTED_FILE}" remote:cql-backups/postgres/

echo "[$(date)] Off-site backup: ${ENCRYPTED_FILE}"

# Cleanup local encrypted files older than 7 days
find "${OFFSITE_DIR}" -name "*.gpg" -mtime +7 -delete
```

### 5.4 Secrets Backup

```bash
#!/bin/bash
# backup-secrets.sh — Encrypted secrets backup
# Run manually after any secret change

set -euo pipefail

SECRETS_BACKUP_DIR="/opt/cql-platform/backups/secrets"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
GPG_RECIPIENT="cql-backup@your-domain.com"

mkdir -p "${SECRETS_BACKUP_DIR}"

# Collect secrets (DO NOT log these values)
cat > "/tmp/cql-secrets-${TIMESTAMP}.env" <<EOF
# CQL Platform Secrets — ${TIMESTAMP}
# HANDLE WITH EXTREME CARE — Contains encryption keys and credentials
JWT_SECRET=${JWT_SECRET}
ENCRYPTION_KEY=${ENCRYPTION_KEY}
DB_PASSWORD=${DB_PASSWORD}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
OKTA_CLIENT_SECRET=${OKTA_CLIENT_SECRET:-}
GF_SECURITY_ADMIN_PASSWORD=${GF_SECURITY_ADMIN_PASSWORD:-}
EOF

# Encrypt and store
gpg --encrypt --recipient "${GPG_RECIPIENT}" \
  --output "${SECRETS_BACKUP_DIR}/secrets_${TIMESTAMP}.env.gpg" \
  "/tmp/cql-secrets-${TIMESTAMP}.env"

# Securely delete plaintext
shred -u "/tmp/cql-secrets-${TIMESTAMP}.env"

# Keep only current + previous backup
ls -t "${SECRETS_BACKUP_DIR}"/secrets_*.env.gpg | tail -n +3 | xargs -r shred -u

echo "[$(date)] Secrets backup complete (encrypted)."
```

---

## 6. Recovery Procedures

### 6.1 PostgreSQL Recovery from pg_dump

```bash
#!/bin/bash
# restore-postgres.sh — Restore PostgreSQL from logical backup
# Usage: ./restore-postgres.sh <backup_file>

set -euo pipefail

BACKUP_FILE="${1:?Usage: restore-postgres.sh <backup_file.sql.gz>}"
CONTAINER_NAME="cql-postgres"
DB_NAME="${POSTGRES_DB:-cqlplatform}"
DB_USER="${POSTGRES_USER:-cqlplatform}"

echo "============================================="
echo "  CQL Platform PostgreSQL Recovery"
echo "  Backup: ${BACKUP_FILE}"
echo "  Target: ${CONTAINER_NAME}/${DB_NAME}"
echo "============================================="
echo ""
echo "WARNING: This will REPLACE all data in ${DB_NAME}!"
read -p "Continue? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

# Verify backup integrity
if [ -f "${BACKUP_FILE}.sha256" ]; then
  echo "Verifying backup checksum..."
  sha256sum -c "${BACKUP_FILE}.sha256"
fi

# Stop backend to prevent writes
echo "Stopping backend..."
cd /opt/cql-platform/docker
docker compose stop backend

# Drop and recreate database
echo "Recreating database..."
docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d postgres \
  -c "DROP DATABASE IF EXISTS ${DB_NAME};"
docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d postgres \
  -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

# Restore from backup
echo "Restoring from backup..."
docker exec -i "${CONTAINER_NAME}" pg_restore \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --verbose \
  --no-owner \
  < "${BACKUP_FILE}"

# Restart backend
echo "Restarting backend..."
docker compose start backend

# Wait for health check
echo "Waiting for backend health..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Backend is healthy!"
    break
  fi
  sleep 2
done

echo "============================================="
echo "  Recovery complete!"
echo "  Restored from: ${BACKUP_FILE}"
echo "  Verify: curl http://localhost:8080/actuator/health"
echo "============================================="
```

### 6.2 Docker Volume Recovery

```bash
#!/bin/bash
# restore-volume.sh — Restore Docker volume from tar backup
# Usage: ./restore-volume.sh <volume_name> <backup_file.tar.gz>

set -euo pipefail

VOLUME_NAME="${1:?Usage: restore-volume.sh <volume_name> <backup_file.tar.gz>}"
BACKUP_FILE="${2:?Usage: restore-volume.sh <volume_name> <backup_file.tar.gz>}"

echo "Restoring volume '${VOLUME_NAME}' from '${BACKUP_FILE}'..."
echo "WARNING: This will REPLACE all data in the volume!"
read -p "Continue? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

# Stop all services using the volume
cd /opt/cql-platform/docker
docker compose down

# Remove and recreate volume
docker volume rm "${VOLUME_NAME}" 2>/dev/null || true
docker volume create "${VOLUME_NAME}"

# Restore data
docker run --rm \
  -v "${VOLUME_NAME}":/data \
  -v "$(dirname "${BACKUP_FILE}")":/backup:ro \
  alpine tar xzf "/backup/$(basename "${BACKUP_FILE}")" -C /data

# Restart services
docker compose up -d

echo "Volume '${VOLUME_NAME}' restored. Services restarting..."
```

### 6.3 Full Platform Recovery (Disaster Recovery)

```bash
#!/bin/bash
# disaster-recovery.sh — Full platform recovery from scratch
# Prerequisites: Docker, Docker Compose, backup files, secrets

set -euo pipefail

echo "============================================="
echo "  CQL Platform — Full Disaster Recovery"
echo "============================================="

# Step 1: Verify prerequisites
echo ""
echo "Step 1: Verifying prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker not installed"; exit 1; }
command -v docker compose >/dev/null 2>&1 || { echo "ERROR: Docker Compose not installed"; exit 1; }

# Step 2: Clone or copy application code
echo "Step 2: Setting up application code..."
DEPLOY_DIR="/opt/cql-platform"
# git clone <repo-url> "${DEPLOY_DIR}" || { echo "Using existing code at ${DEPLOY_DIR}"; }

# Step 3: Restore secrets
echo "Step 3: Restoring secrets..."
if [ ! -f "${DEPLOY_DIR}/docker/.env" ]; then
  echo "ERROR: .env file not found. Decrypt from secrets backup:"
  echo "  gpg --decrypt secrets_YYYYMMDD.env.gpg > ${DEPLOY_DIR}/docker/.env"
  exit 1
fi

# Step 4: Start infrastructure (database only)
echo "Step 4: Starting PostgreSQL..."
cd "${DEPLOY_DIR}/docker"
docker compose up -d postgres
sleep 10  # Wait for PostgreSQL initialization

# Step 5: Restore database
echo "Step 5: Restoring database from backup..."
LATEST_BACKUP=$(ls -t /opt/cql-platform/backups/postgres/cqlplatform_*.sql.gz 2>/dev/null | head -1)
if [ -n "${LATEST_BACKUP}" ]; then
  docker exec -i cql-postgres pg_restore \
    -U "${POSTGRES_USER:-cqlplatform}" \
    -d "${POSTGRES_DB:-cqlplatform}" \
    --no-owner \
    < "${LATEST_BACKUP}"
  echo "Database restored from: ${LATEST_BACKUP}"
else
  echo "WARNING: No backup found. Database will be empty (Flyway will create schema)."
fi

# Step 6: Start remaining services
echo "Step 6: Starting all services..."
docker compose up -d

# Step 7: Health verification
echo "Step 7: Verifying services..."
echo "Waiting for services to start..."
sleep 30

SERVICES=("http://localhost:8080/actuator/health" "http://localhost:8888")
for svc in "${SERVICES[@]}"; do
  if curl -sf "${svc}" > /dev/null 2>&1; then
    echo "  ✓ ${svc} — Healthy"
  else
    echo "  ✗ ${svc} — Not responding (may still be starting)"
  fi
done

echo ""
echo "============================================="
echo "  Disaster Recovery Complete"
echo "============================================="
echo ""
echo "Post-recovery checklist:"
echo "  [ ] Verify user login works"
echo "  [ ] Verify measure data is intact"
echo "  [ ] Verify audit logs are present"
echo "  [ ] Verify FHIR connectivity"
echo "  [ ] Verify CDS Hooks endpoints respond"
echo "  [ ] Check Grafana dashboards"
echo "  [ ] Notify stakeholders of recovery"
echo "  [ ] Document incident and recovery timeline"
```

---

## 7. Disaster Recovery Plan

### 7.1 Disaster Scenarios

| Scenario | Probability | Impact | Recovery Strategy |
|---|---|---|---|
| Single container crash | High | Low | Docker auto-restart (restart: unless-stopped) |
| Database corruption | Low | High | pg_dump restore + WAL replay |
| Host server failure | Low | High | New server + Docker Compose + backup |
| Network outage | Medium | Medium | Wait for resolution; circuit breakers prevent cascading failure |
| Data center failure | Very Low | Critical | DR site activation with remote backups |
| Ransomware/security breach | Low | Critical | Isolated backup restore, incident response |
| Accidental data deletion | Medium | High | Point-in-time recovery via WAL or pg_dump |

### 7.2 Recovery Priority Order

```
Priority 1 (Immediate):
  └── PostgreSQL database (all application state)
      └── Restore from latest backup + WAL replay

Priority 2 (Within 1 hour):
  └── Backend application (Spring Boot)
      └── Docker Compose up (stateless, just needs database)
  └── Frontend (Nginx + React SPA)
      └── Docker Compose up (static files, proxies to backend)

Priority 3 (Within 4 hours):
  └── HAPI FHIR server (optional, external FHIR servers may be primary)
  └── Monitoring stack (Prometheus, Grafana, AlertManager)

Priority 4 (Within 24 hours):
  └── Grafana dashboard configurations
  └── Alert rules and notification channels
  └── Taiwan FHIR Generator service
```

### 7.3 Communication Plan

| Phase | Action | Responsible | Channel |
|---|---|---|---|
| Detection | Identify and classify incident | On-call engineer | AlertManager → Slack/Email |
| Escalation | Notify stakeholders if RTO at risk | On-call engineer | Email, phone |
| Updates | Status updates every 30 minutes during recovery | Recovery lead | Status page, email |
| Resolution | Confirm recovery, post-mortem scheduling | Recovery lead | All channels |
| Post-mortem | Root cause analysis within 48 hours | Security team | Internal meeting |

---

## 8. Testing & Validation

### 8.1 Backup Verification Schedule

| Test | Frequency | Method | Success Criteria |
|---|---|---|---|
| Backup file integrity | Daily (automated) | SHA-256 checksum verification | Checksum matches |
| Backup completeness | Weekly (automated) | Table row count comparison | Counts within 1% |
| pg_dump restore test | Monthly | Restore to test environment | All tables present, app starts |
| Full DR simulation | Quarterly | Complete recovery on clean server | RTO met, data verified |
| Secrets recovery test | Semi-annually | Decrypt and verify secrets backup | All secrets recoverable |

### 8.2 Backup Verification Script

```bash
#!/bin/bash
# verify-backup.sh — Verify latest backup integrity
# Schedule: 0 6 * * * /opt/cql-platform/scripts/verify-backup.sh

set -euo pipefail

BACKUP_DIR="/opt/cql-platform/backups/postgres"
LATEST=$(ls -t "${BACKUP_DIR}"/cqlplatform_*.sql.gz | head -1)

if [ -z "${LATEST}" ]; then
  echo "CRITICAL: No backup files found!" >&2
  exit 1
fi

# Check file age (should be < 26 hours for daily backup)
FILE_AGE=$(( $(date +%s) - $(stat -c %Y "${LATEST}") ))
if [ ${FILE_AGE} -gt 93600 ]; then
  echo "WARNING: Latest backup is $(( FILE_AGE / 3600 )) hours old" >&2
fi

# Verify checksum
if [ -f "${LATEST}.sha256" ]; then
  sha256sum -c "${LATEST}.sha256" || {
    echo "CRITICAL: Backup checksum mismatch!" >&2
    exit 1
  }
fi

# Verify backup is readable
docker exec cql-postgres pg_restore --list < "${LATEST}" > /dev/null 2>&1 || {
  echo "CRITICAL: Backup is not readable by pg_restore!" >&2
  exit 1
}

# Report
FILE_SIZE=$(du -h "${LATEST}" | cut -f1)
echo "[$(date)] Backup OK: ${LATEST} (${FILE_SIZE}, age: $(( FILE_AGE / 3600 ))h)"
```

### 8.3 Monthly Recovery Test Procedure

1. **Prepare test environment**: Provision a clean Docker host or use a staging server
2. **Copy latest backup**: Transfer pg_dump file to test environment
3. **Run recovery**: Execute `disaster-recovery.sh` with test configuration
4. **Validate data**:
   - Login as admin user
   - Verify measure count matches production
   - Verify audit log entries are present
   - Run a CQL execution against a known measure
   - Check FHIR connectivity
5. **Document results**: Record test date, backup used, recovery time, issues found
6. **Cleanup**: Destroy test environment

### 8.4 Recovery Test Log

| Date | Backup Used | Recovery Time | Data Verified | Issues | Tester |
|---|---|---|---|---|---|
| — | — | — | — | — | — |

---

## 9. Monitoring & Alerting

### 9.1 Backup Monitoring

| Metric | Alert Condition | Severity |
|---|---|---|
| Last backup age | > 26 hours (no daily backup) | Critical |
| Backup file size | Decrease > 50% from previous | Warning |
| Checksum verification | Mismatch | Critical |
| Disk space (backup volume) | < 20% free | Warning |
| Disk space (backup volume) | < 10% free | Critical |
| Off-site transfer status | Transfer failed | Warning |

### 9.2 Database Health Monitoring

| Metric | Alert Condition | Severity |
|---|---|---|
| PostgreSQL up | Down for > 30 seconds | Critical |
| Connection pool | > 80% utilized | Warning |
| Replication lag (if applicable) | > 60 seconds | Warning |
| Table bloat | > 50% bloat ratio | Warning |
| Long-running queries | > 120 seconds | Warning |

### 9.3 Prometheus Alert Rules (Example)

```yaml
# prometheus/alerts/backup.yml
groups:
  - name: backup_alerts
    rules:
      - alert: BackupStale
        expr: (time() - backup_last_success_timestamp_seconds) > 93600
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL backup is stale"
          description: "No successful backup in the last 26 hours"

      - alert: BackupSizeAnomaly
        expr: |
          backup_file_size_bytes < (backup_file_size_bytes offset 1d) * 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Backup size decreased significantly"
          description: "Today's backup is less than 50% of yesterday's size"
```

---

## 10. Roles & Responsibilities

| Role | Responsibilities |
|---|---|
| **Database Administrator** | Configure backups, monitor backup health, perform recovery |
| **Platform Administrator** | Manage secrets, coordinate DR testing, maintain documentation |
| **On-call Engineer** | Respond to backup/recovery alerts, execute recovery procedures |
| **Security Team** | Manage encryption keys, audit backup access, secrets rotation |
| **Operations Lead** | Schedule DR tests, review test results, approve recovery plans |

### Escalation Path

```
Level 1: On-call Engineer (automated alert)
  └── Diagnose, attempt auto-recovery
Level 2: Database Administrator (30 min escalation)
  └── Manual recovery from backup
Level 3: Platform Administrator (1 hour escalation)
  └── Full disaster recovery coordination
Level 4: Management (2 hour escalation)
  └── Business impact communication, external coordination
```

---

## Appendix A: Backup File Locations

| Type | Local Path | Remote Path |
|---|---|---|
| PostgreSQL pg_dump | `/opt/cql-platform/backups/postgres/` | `s3://cql-backups/postgres/` |
| Docker volumes | `/opt/cql-platform/backups/volumes/` | `s3://cql-backups/volumes/` |
| Off-site encrypted | `/opt/cql-platform/backups/offsite/` | `s3://cql-backups/offsite/` |
| Secrets (encrypted) | `/opt/cql-platform/backups/secrets/` | Physical secure storage |
| WAL archive | Docker volume (PostgreSQL internal) | — |

## Appendix B: Recovery Checklists

### Quick Reference: Single Service Recovery

- [ ] Identify failed service: `docker compose ps`
- [ ] Check logs: `docker compose logs <service> --tail 100`
- [ ] Restart service: `docker compose restart <service>`
- [ ] Verify health: `curl http://localhost:8080/actuator/health`

### Quick Reference: Database Recovery

- [ ] Stop backend: `docker compose stop backend`
- [ ] Identify latest backup: `ls -lt /opt/cql-platform/backups/postgres/`
- [ ] Verify checksum: `sha256sum -c <backup>.sha256`
- [ ] Restore: Run `restore-postgres.sh <backup_file>`
- [ ] Restart backend: `docker compose start backend`
- [ ] Verify health: `curl http://localhost:8080/actuator/health`
- [ ] Spot-check data: Login, verify records
- [ ] Document incident

### Quick Reference: Full DR

- [ ] Provision new infrastructure
- [ ] Install Docker and Docker Compose
- [ ] Clone application repository
- [ ] Decrypt and restore `.env` secrets
- [ ] Transfer backup files to new server
- [ ] Run `disaster-recovery.sh`
- [ ] Verify all services healthy
- [ ] Verify data integrity
- [ ] Update DNS (if applicable)
- [ ] Notify stakeholders
- [ ] Schedule post-mortem
