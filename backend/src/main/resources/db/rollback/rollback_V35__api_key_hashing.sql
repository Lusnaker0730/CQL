-- Rollback V35: API Key Hashing

ALTER TABLE user_api_keys DROP COLUMN IF EXISTS key_prefix;
