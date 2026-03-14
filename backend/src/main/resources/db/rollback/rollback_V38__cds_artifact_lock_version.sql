-- Rollback V38: CDS Artifact Lock Version

ALTER TABLE cds_artifact DROP COLUMN IF EXISTS lock_version;
