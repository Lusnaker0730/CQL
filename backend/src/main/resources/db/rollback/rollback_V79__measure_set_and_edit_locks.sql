-- Rollback V79: drop the measure set lineage and the test case / CQL library edit locks (PAT-253).
--
-- Version lineage falls back to (tenant_id, name) as before V79 — versions that were renamed
-- after the upgrade will no longer appear in each other's history. Held locks are simply dropped.
-- Roll the application back to a pre-PAT-253 image at the same time (ddl-auto: validate expects
-- the columns).

ALTER TABLE cql_library DROP COLUMN IF EXISTS locked_at;
ALTER TABLE cql_library DROP COLUMN IF EXISTS locked_by;

ALTER TABLE test_case DROP COLUMN IF EXISTS locked_at;
ALTER TABLE test_case DROP COLUMN IF EXISTS locked_by;

DROP INDEX IF EXISTS idx_measure_definition_set;
ALTER TABLE measure_definition DROP CONSTRAINT IF EXISTS fk_measure_definition_measure_set;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS measure_set_id;

DROP TABLE IF EXISTS measure_set;
