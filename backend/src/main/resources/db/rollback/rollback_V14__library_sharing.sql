-- Rollback V14: Library Sharing

DROP INDEX IF EXISTS idx_cql_library_owner;
DROP INDEX IF EXISTS idx_cql_library_access;

ALTER TABLE cql_library DROP COLUMN IF EXISTS owner_username;
ALTER TABLE cql_library DROP COLUMN IF EXISTS shared_with;
ALTER TABLE cql_library DROP COLUMN IF EXISTS access_level;
