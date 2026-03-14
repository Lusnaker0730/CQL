-- Rollback V41: User Token Version

ALTER TABLE app_user DROP COLUMN IF EXISTS token_version;
