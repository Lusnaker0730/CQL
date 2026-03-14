-- Password reset feature: email_hash for lookup, force password change flag, and reset tokens

-- Add email_hash column for deterministic email lookup (SHA-256)
ALTER TABLE app_user ADD COLUMN email_hash VARCHAR(64);
CREATE INDEX idx_app_user_email_hash ON app_user(email_hash);

-- Add force password change flag for admin-initiated resets
ALTER TABLE app_user ADD COLUMN force_password_change BOOLEAN DEFAULT FALSE;

-- Password reset token table
CREATE TABLE IF NOT EXISTS password_reset_token (
    id          BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token_hash  VARCHAR(64)     NOT NULL,
    expires_at  TIMESTAMP       NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_reset_token_hash ON password_reset_token(token_hash);
CREATE INDEX idx_reset_token_user ON password_reset_token(user_id);
