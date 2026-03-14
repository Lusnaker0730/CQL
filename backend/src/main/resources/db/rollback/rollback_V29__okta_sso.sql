-- Rollback V29: Okta SSO
-- WARNING: This re-adds NOT NULL on password. Will fail if any SSO-only users
-- exist (password IS NULL). Migrate those users first.

DROP INDEX IF EXISTS idx_user_external_id;

ALTER TABLE app_user DROP COLUMN IF EXISTS auth_provider;
ALTER TABLE app_user DROP COLUMN IF EXISTS external_id;
ALTER TABLE app_user DROP COLUMN IF EXISTS display_name;

ALTER TABLE app_user ALTER COLUMN password SET NOT NULL;
