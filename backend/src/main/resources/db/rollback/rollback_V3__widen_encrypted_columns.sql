-- Rollback V3: Widen Encrypted Columns
-- Revert app_user.email from VARCHAR(500) back to VARCHAR(255)
-- WARNING: Will fail if any existing email exceeds 255 characters.

ALTER TABLE app_user ALTER COLUMN email TYPE VARCHAR(255);
