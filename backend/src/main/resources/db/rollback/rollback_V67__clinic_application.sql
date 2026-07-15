-- Rollback V67: drop the clinic application table.
DROP INDEX IF EXISTS idx_clinic_application_status;
DROP TABLE IF EXISTS clinic_application;
