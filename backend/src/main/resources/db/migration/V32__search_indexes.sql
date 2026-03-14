-- Full-text search optimization indexes
-- Speeds up LIKE '%keyword%' queries on name/title/description columns

-- CQL Library: name and description search
CREATE INDEX IF NOT EXISTS idx_cql_lib_name_lower ON cql_library(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_cql_lib_desc_lower ON cql_library(LOWER(description));

-- Measure Definition: name and title search
CREATE INDEX IF NOT EXISTS idx_measure_def_name_lower ON measure_definition(LOWER(name));
CREATE INDEX IF NOT EXISTS idx_measure_def_title_lower ON measure_definition(LOWER(title));

-- Shared-with filtering (used by findSharedWithUser queries)
CREATE INDEX IF NOT EXISTS idx_cql_lib_shared ON cql_library(access_level);
CREATE INDEX IF NOT EXISTS idx_measure_def_shared ON measure_definition(access_level);
