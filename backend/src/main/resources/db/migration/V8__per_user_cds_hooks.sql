-- Per-user CDS Hooks management
-- Add ownership and sharing columns to cds_service_config
ALTER TABLE cds_service_config ADD COLUMN owner_username VARCHAR(255);
ALTER TABLE cds_service_config ADD COLUMN is_shared BOOLEAN DEFAULT FALSE;

-- Create user API keys table for external EHR integration
CREATE TABLE user_api_keys (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    active BOOLEAN DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_cds_service_owner ON cds_service_config(owner_username);
CREATE INDEX idx_user_api_key ON user_api_keys(api_key);
CREATE INDEX idx_user_api_keys_username ON user_api_keys(username);

-- Migrate existing services: assign to admin and mark as shared
UPDATE cds_service_config SET owner_username = 'admin', is_shared = TRUE WHERE owner_username IS NULL;
