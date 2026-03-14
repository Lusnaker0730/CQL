-- Rollback V8: Per-User CDS Hooks Management

DROP TABLE IF EXISTS user_api_keys CASCADE;

ALTER TABLE cds_service_config DROP COLUMN IF EXISTS owner_username;
ALTER TABLE cds_service_config DROP COLUMN IF EXISTS is_shared;
