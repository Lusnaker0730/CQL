-- Rollback V78: drop the test case validation columns (PAT-245).
--
-- Validation outcomes are lost (they are recomputed on demand); nothing else references the
-- columns. Roll the application back to a pre-PAT-245 image at the same time (ddl-auto: validate
-- expects them).

ALTER TABLE test_case DROP COLUMN IF EXISTS validated_at;
ALTER TABLE test_case DROP COLUMN IF EXISTS validation_summary;
ALTER TABLE test_case DROP COLUMN IF EXISTS validation_status;
