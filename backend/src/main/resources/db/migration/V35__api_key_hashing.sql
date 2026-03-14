-- Add key_prefix column for display purposes (API keys are now stored as SHA-256 hashes)
ALTER TABLE user_api_keys ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(12);

-- Populate key_prefix from existing plaintext keys (before they get hashed on next use)
UPDATE user_api_keys SET key_prefix = SUBSTRING(api_key, 1, 8) WHERE key_prefix IS NULL AND LENGTH(api_key) > 8;
