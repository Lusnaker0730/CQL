-- V36: Add explicit PHI access tracking columns to audit_log
-- Replaces fragile LIKE '%patient%' heuristic with a proper boolean flag

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS phi_access BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS query_parameters VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_audit_phi_access ON audit_log(phi_access) WHERE phi_access = TRUE;
