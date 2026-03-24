-- Rollback V43: Revert SMART Backend Services changes
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS token_endpoint;
ALTER TABLE ehr_connection ALTER COLUMN credentials TYPE VARCHAR(2000);
ALTER TABLE ehr_connection ALTER COLUMN auth_type TYPE VARCHAR(20);
