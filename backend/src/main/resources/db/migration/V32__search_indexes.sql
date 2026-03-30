-- Full-text search optimization indexes
-- Speeds up LIKE '%keyword%' queries on name/title/description columns

-- CQL Library: name and description search (standard composite index for case-insensitive queries)
CREATE INDEX IF NOT EXISTS idx_cql_lib_name ON cql_library(name);
CREATE INDEX IF NOT EXISTS idx_cql_lib_desc ON cql_library(description);

-- Measure Definition: name and title search
CREATE INDEX IF NOT EXISTS idx_measure_def_name ON measure_definition(name);
CREATE INDEX IF NOT EXISTS idx_measure_def_title ON measure_definition(title);

-- Shared-with filtering (used by findSharedWithUser queries)
CREATE INDEX IF NOT EXISTS idx_cql_lib_shared ON cql_library(access_level);
CREATE INDEX IF NOT EXISTS idx_measure_def_shared ON measure_definition(access_level);
