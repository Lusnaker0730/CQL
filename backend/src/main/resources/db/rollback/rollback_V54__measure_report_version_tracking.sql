-- Rollback for V54__measure_report_version_tracking.sql
-- Drops version/hash columns and the supporting index. Historical data in those
-- columns is lost on rollback; the underlying measure_definition rows still have
-- version/cql_content/elm_json so reports can be re-bound manually if needed.

DROP INDEX IF EXISTS idx_measure_report_definition_version;

ALTER TABLE measure_report
    DROP COLUMN IF EXISTS elm_hash,
    DROP COLUMN IF EXISTS cql_hash,
    DROP COLUMN IF EXISTS measure_version;
