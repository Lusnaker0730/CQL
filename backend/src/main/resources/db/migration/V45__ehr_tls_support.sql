-- V45: Add TLS/mTLS configuration fields to ehr_connection
ALTER TABLE ehr_connection ADD COLUMN tls_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE ehr_connection ADD COLUMN ca_cert_pem TEXT;
ALTER TABLE ehr_connection ADD COLUMN client_cert_pem TEXT;
ALTER TABLE ehr_connection ADD COLUMN client_key_pem TEXT;
ALTER TABLE ehr_connection ADD COLUMN tls_min_version VARCHAR(10) DEFAULT 'TLSv1.2';
ALTER TABLE ehr_connection ADD COLUMN hostname_verification BOOLEAN DEFAULT TRUE;
