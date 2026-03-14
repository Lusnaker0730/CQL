-- V41: Add token_version to app_user for JWT immediate revocation.
-- When bumped, all previously-issued access tokens for the user become invalid.
ALTER TABLE app_user ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
