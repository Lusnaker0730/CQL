-- V43: Support SMART Backend Services authentication for EHR connections
-- Widen auth_type for 'smart_backend', widen credentials for PEM private keys,
-- add token_endpoint column

ALTER TABLE ehr_connection ALTER COLUMN auth_type TYPE VARCHAR(50);
ALTER TABLE ehr_connection ALTER COLUMN credentials TYPE TEXT;
ALTER TABLE ehr_connection ADD COLUMN token_endpoint VARCHAR(500);
