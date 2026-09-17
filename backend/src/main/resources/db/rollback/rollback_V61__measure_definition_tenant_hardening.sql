-- Rollback V61: revert measure_definition tenant hardening.
-- NOTE: re-adding UNIQUE (name, version) FAILS if two tenants already hold the same
-- name+version (which V61 permitted). Resolve such duplicates before rolling back.
ALTER TABLE measure_definition DROP CONSTRAINT IF EXISTS uq_measure_definition_tenant_name_version;
ALTER TABLE measure_definition ADD CONSTRAINT measure_definition_name_version_key UNIQUE (name, version);
ALTER TABLE measure_definition ALTER COLUMN tenant_id DROP NOT NULL;
