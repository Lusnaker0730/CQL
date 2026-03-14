-- Rollback V31: Performance Indexes

DROP INDEX IF EXISTS idx_measure_def_owner;
DROP INDEX IF EXISTS idx_measure_def_dept_status;
DROP INDEX IF EXISTS idx_measure_def_access;
DROP INDEX IF EXISTS idx_measure_report_created;
DROP INDEX IF EXISTS idx_measure_report_measure_created;
DROP INDEX IF EXISTS idx_measure_report_dept;
DROP INDEX IF EXISTS idx_cql_lib_owner;
DROP INDEX IF EXISTS idx_cql_lib_access;
