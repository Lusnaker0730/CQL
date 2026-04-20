-- Rollback for V53__user_password_lockout.sql
-- Removes lockout tracking columns + index. Running this drops the history
-- of past failed attempts; only do this on a fresh install where lockout
-- behavior isn't desired.

DROP INDEX IF EXISTS idx_app_user_lockout_until;

ALTER TABLE app_user
    DROP COLUMN IF EXISTS lockout_until,
    DROP COLUMN IF EXISTS failed_login_attempts;
