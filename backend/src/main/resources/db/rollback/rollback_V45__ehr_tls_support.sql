ALTER TABLE ehr_connection DROP COLUMN IF EXISTS tls_enabled;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS ca_cert_pem;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS client_cert_pem;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS client_key_pem;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS tls_min_version;
ALTER TABLE ehr_connection DROP COLUMN IF EXISTS hostname_verification;
