-- V56: Tenant (Clinic) — hard multi-tenancy foundation
--
-- Introduces a Tenant entity as the isolation boundary for the multi-clinic platform
-- (Roadmap Phase 2). A clinic = a tenant; the existing `department` (V26) stays as an
-- intra-tenant sub-dimension.
--
-- This migration is ADDITIVE and INERT: it only creates the tenant table and a nullable
-- app_user.tenant_id. Nothing is enforced yet — row-level isolation lands in follow-up
-- migrations that add tenant_id to each resource (ehr_connection / cql_library /
-- measure_definition / measure_report) and backfill existing rows to a default tenant.

CREATE TABLE tenant (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Assign users to a tenant. Nullable for now: existing users have no tenant until
-- explicitly assigned, and enforcement is deferred, so this changes no behaviour yet.
ALTER TABLE app_user ADD COLUMN tenant_id BIGINT;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);
