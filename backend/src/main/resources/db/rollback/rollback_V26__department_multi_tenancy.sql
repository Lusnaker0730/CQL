-- Rollback V26: Department Multi-Tenancy

DROP INDEX IF EXISTS idx_user_department;
DROP INDEX IF EXISTS idx_measure_def_department;
DROP INDEX IF EXISTS idx_measure_report_department;

ALTER TABLE app_user DROP COLUMN IF EXISTS department;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS department;
ALTER TABLE measure_report DROP COLUMN IF EXISTS department;

DROP TABLE IF EXISTS department CASCADE;
