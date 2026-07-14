-- Rollback V63: revert measure_report tenant hardening.
-- The backfill (NULL -> default tenant) is not reverted — those assignments are correct data.
ALTER TABLE measure_report ALTER COLUMN tenant_id DROP NOT NULL;
