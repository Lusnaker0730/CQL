-- Rollback V82: drop the terminology pins and the per-report terminology provenance (PAT-260).
--
-- Pinned measures go back to following the newest active version of each value set (the pre-PAT-260
-- behaviour); reports lose the record of which versions they used. Roll the application back with it.
ALTER TABLE measure_report DROP COLUMN IF EXISTS terminology_versions;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS value_set_versions;
