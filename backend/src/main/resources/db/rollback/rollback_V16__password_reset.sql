-- Rollback V16: Password Reset

DROP TABLE IF EXISTS password_reset_token CASCADE;

DROP INDEX IF EXISTS idx_app_user_email_hash;

ALTER TABLE app_user DROP COLUMN IF EXISTS email_hash;
ALTER TABLE app_user DROP COLUMN IF EXISTS force_password_change;
