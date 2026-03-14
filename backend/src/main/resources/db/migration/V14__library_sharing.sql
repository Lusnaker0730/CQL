-- Library sharing & ownership
ALTER TABLE cql_library ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE cql_library ADD COLUMN IF NOT EXISTS shared_with TEXT DEFAULT '[]';
ALTER TABLE cql_library ADD COLUMN IF NOT EXISTS access_level VARCHAR(20) DEFAULT 'private';

CREATE INDEX IF NOT EXISTS idx_cql_library_owner ON cql_library(owner_username);
CREATE INDEX IF NOT EXISTS idx_cql_library_access ON cql_library(access_level);
