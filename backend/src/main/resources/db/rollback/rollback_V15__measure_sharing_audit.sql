-- Rollback V15: Measure Sharing and Audit

DROP TABLE IF EXISTS measure_audit CASCADE;

DROP INDEX IF EXISTS idx_measure_def_owner;
DROP INDEX IF EXISTS idx_measure_def_access;

ALTER TABLE measure_definition DROP COLUMN IF EXISTS owner_username;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS shared_with;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS access_level;
