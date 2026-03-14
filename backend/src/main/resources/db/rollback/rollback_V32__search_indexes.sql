-- Rollback V32: Search Indexes

DROP INDEX IF EXISTS idx_cql_lib_name_lower;
DROP INDEX IF EXISTS idx_cql_lib_desc_lower;
DROP INDEX IF EXISTS idx_measure_def_name_lower;
DROP INDEX IF EXISTS idx_measure_def_title_lower;
DROP INDEX IF EXISTS idx_cql_lib_shared;
DROP INDEX IF EXISTS idx_measure_def_shared;
