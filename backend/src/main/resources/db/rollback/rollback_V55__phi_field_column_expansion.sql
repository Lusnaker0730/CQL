-- Rollback V55: revert PHI columns to original VARCHAR lengths.
-- CAUTION: if any encrypted row exists (length > VARCHAR limit), this rollback
-- will truncate or fail. Before running, either:
--   (a) decrypt and verify all rows fit within the target VARCHAR length, or
--   (b) accept that rollback is destructive for rows encrypted after V55.
-- Generally only run rollback on an environment that has not yet received
-- encrypted writes.

ALTER TABLE patient_import
    ALTER COLUMN patient_fhir_id    TYPE VARCHAR(200),
    ALTER COLUMN patient_identifier TYPE VARCHAR(200),
    ALTER COLUMN patient_name       TYPE VARCHAR(500);

ALTER TABLE sandbox_preset
    ALTER COLUMN patient_id TYPE VARCHAR(100);
