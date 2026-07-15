-- V67: Clinic self-service application (#700 PR-1)
--
-- A prospective clinic submits an application from the public site; the platform
-- operator reviews it and, on approval, a tenant + its first tenant-admin account are
-- provisioned (reusing the #699 tenant machinery). admin_email is stored encrypted
-- (EncryptionConverter, ENC: prefix) — same handling as app_user.email.

CREATE TABLE clinic_application (
    id BIGSERIAL PRIMARY KEY,
    clinic_name VARCHAR(200) NOT NULL,
    tenant_code VARCHAR(50) NOT NULL,
    admin_username VARCHAR(100) NOT NULL,
    admin_email VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    rejection_reason VARCHAR(500),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    created_tenant_id BIGINT REFERENCES tenant(id),
    created_user_id BIGINT REFERENCES app_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clinic_application_status ON clinic_application(status);
