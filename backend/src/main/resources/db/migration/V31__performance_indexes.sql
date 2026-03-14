-- Performance indexes for common query paths

-- measure_definition: owner and department filtering
CREATE INDEX IF NOT EXISTS idx_measure_def_owner ON measure_definition(owner_username);
CREATE INDEX IF NOT EXISTS idx_measure_def_dept_status ON measure_definition(department, status);
CREATE INDEX IF NOT EXISTS idx_measure_def_access ON measure_definition(access_level);

-- measure_report: recent reports and per-measure lookups
CREATE INDEX IF NOT EXISTS idx_measure_report_created ON measure_report(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_measure_report_measure_created ON measure_report(measure_definition_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_measure_report_dept ON measure_report(department);

-- cql_library: owner and access filtering
CREATE INDEX IF NOT EXISTS idx_cql_lib_owner ON cql_library(owner_username);
CREATE INDEX IF NOT EXISTS idx_cql_lib_access ON cql_library(access_level);
