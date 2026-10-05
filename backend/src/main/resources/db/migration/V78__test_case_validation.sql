-- PAT-245: FHIR validation outcome of a test case's patient bundle.
-- A save queues a HAPI validation of the bundle (TW Core profiles when the IG is loaded); the
-- outcome is stored here so the list can show Valid / Invalid / Pending and the run-all can skip
-- invalid cases. NULL status = created before PAT-245 and never validated (no backfill; the
-- "Validate all" action queues them).
--   validation_status  pending | valid | invalid | error
--   validation_summary JSON TestCaseValidation (counts + error issues, capped)
-- test_case is under row-level security since V70; adding columns does not touch the policy.

ALTER TABLE test_case ADD COLUMN validation_status VARCHAR(20);
ALTER TABLE test_case ADD COLUMN validation_summary TEXT;
ALTER TABLE test_case ADD COLUMN validated_at TIMESTAMP;
