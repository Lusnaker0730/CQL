-- Rollback V62: revert cql_library tenant hardening.
-- NOTE: re-adding UNIQUE (name, version) FAILS if two tenants already hold the same
-- name+version (which V62 permitted). Resolve such duplicates before rolling back.
ALTER TABLE cql_library DROP CONSTRAINT IF EXISTS uq_cql_library_tenant_name_version;
ALTER TABLE cql_library ADD CONSTRAINT uq_library_name_version UNIQUE (name, version);
ALTER TABLE cql_library ALTER COLUMN tenant_id DROP NOT NULL;
