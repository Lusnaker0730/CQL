-- Rollback V80: drop the metadata fill-in columns (PAT-256).
--
-- The values are descriptive metadata only (abbreviated title, endorsement, risk adjustment
-- description on the artifact) and are lost. Roll the application back to a pre-PAT-256 image at
-- the same time (ddl-auto: validate expects the columns).

ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS risk_adjustment_description;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS endorsement_id;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS endorser;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS ecqm_title;

ALTER TABLE measure_definition DROP COLUMN IF EXISTS endorsement_id;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS endorser;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS ecqm_title;
