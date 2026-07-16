-- Rollback V69: revert patient_import to EHR-connection-only imports.
--
-- WARNING: re-adding NOT NULL on connection_id FAILS if any 'fhir-upload' rows exist
-- (they have connection_id = NULL). Delete or reassign those rows before rolling back:
--   DELETE FROM patient_import WHERE source = 'fhir-upload';
-- Take a backup first — this discards uploaded-bundle imports.

ALTER TABLE patient_import DROP COLUMN IF EXISTS source;
ALTER TABLE patient_import ALTER COLUMN connection_id SET NOT NULL;
