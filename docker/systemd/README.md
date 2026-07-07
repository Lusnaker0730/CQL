# Automated DB backup (systemd timer)

Source of truth for the daily PostgreSQL backup on the production VM. These units
run `docker/scripts/backup-db.sh` once a day (pg_dump → gzip → 30-day retention),
and the script also writes a Prometheus freshness metric consumed by the
`BackupStale` / `BackupMetricMissing` alerts.

> **Why a timer, not cron?** systemd gives us `journalctl -u cql-db-backup`,
> `Persistent=true` (a backup missed while the VM was off runs on next boot), and
> `systemctl list-timers` visibility. The VM already runs systemd.

## Install (one-time, on the VM as root)

```bash
cd /opt/CQL   # && git pull to get these files

# 1. Link the units (symlink so `git pull` updates them in place)
ln -sf /opt/CQL/docker/systemd/cql-db-backup.service /etc/systemd/system/cql-db-backup.service
ln -sf /opt/CQL/docker/systemd/cql-db-backup.timer   /etc/systemd/system/cql-db-backup.timer

# 2. Enable + start the timer
systemctl daemon-reload
systemctl enable --now cql-db-backup.timer

# 3. Verify
systemctl list-timers cql-db-backup.timer      # shows NEXT run time
systemctl start cql-db-backup.service          # run one backup now
journalctl -u cql-db-backup.service --no-pager | tail -20
ls -lh /opt/CQL/docker/postgres-backup/        # a fresh cqlplatform_*.sql.gz
```

## Restore

See `docker/scripts/restore-db.sh` and DEPLOYMENT_GUIDE §9.2. Backups are plain
`pg_dump` piped through gzip, restored with `gunzip | psql` (NOT `pg_restore` —
that only reads custom/tar-format dumps).

## Scope / limitations

- **Local only.** Backups live on the same VM as the database — a whole-host loss
  still loses both. Off-site replication (rclone/S3/B2 + gpg) is a documented
  follow-up, deferred by choice.
- **This is logical backup (pg_dump), not PITR.** WAL archiving to `/backups/wal`
  is fixed (see docker-compose.yml `archive_command`) and keeps WAL segments, but
  point-in-time recovery additionally needs a periodic base backup
  (`pg_basebackup`) — not yet wired up.
