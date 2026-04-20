-- Password lockout support (PAT-094)
-- Tracks consecutive failed login attempts and lockout expiry per user.
-- Populated by LoginAttemptListener; consumed by CustomUserDetailsService
-- which blocks authentication while lockout_until is in the future.

ALTER TABLE app_user
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN lockout_until TIMESTAMP WITHOUT TIME ZONE NULL;

-- Partial index covers the hot read path: "is this user currently locked?"
-- Only non-null lockout_until values matter; rows with NULL (vast majority)
-- stay out of the index. Kept cheap to maintain.
CREATE INDEX idx_app_user_lockout_until
    ON app_user (lockout_until)
    WHERE lockout_until IS NOT NULL;
