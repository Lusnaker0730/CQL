CREATE TABLE IF NOT EXISTS refresh_token (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_hash          VARCHAR(64)  NOT NULL,
    user_id             BIGINT       NOT NULL,
    family_id           VARCHAR(36)  NOT NULL,
    expires_at          TIMESTAMP    NOT NULL,
    absolute_expires_at TIMESTAMP    NOT NULL,
    revoked             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_refresh_token_hash ON refresh_token(token_hash);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_family ON refresh_token(family_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token(expires_at);
