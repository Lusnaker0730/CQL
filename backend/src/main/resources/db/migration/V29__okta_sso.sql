-- Okta SSO support: auth provider tracking and external identity
ALTER TABLE app_user ALTER COLUMN password DROP NOT NULL;
ALTER TABLE app_user ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE app_user ADD COLUMN external_id VARCHAR(255);
ALTER TABLE app_user ADD COLUMN display_name VARCHAR(200);
CREATE UNIQUE INDEX idx_user_external_id ON app_user(auth_provider, external_id);
