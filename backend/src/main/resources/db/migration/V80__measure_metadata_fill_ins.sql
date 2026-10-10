-- PAT-256: the standard metadata the platform still did not model (MADiE gap item 12).
--   ecqm_title      eCQM abbreviated title / short name (QM IG `short-name` identifier on export)
--   endorser        endorsing organisation (Measure.endorser ContactDetail on export)
--   endorsement_id  the endorser's identifier for the measure (QM IG `endorser` identifier on export)
-- measure_definition already had supplemental_data_guidance / risk_adjustment_description but no UI
-- for them; the eCQM workspace artifact had the guidance but not the risk adjustment description.
-- Nullable, no backfill; publish copies artifact -> measure only when the artifact has a value.

ALTER TABLE measure_definition ADD COLUMN ecqm_title VARCHAR(200);
ALTER TABLE measure_definition ADD COLUMN endorser VARCHAR(200);
ALTER TABLE measure_definition ADD COLUMN endorsement_id VARCHAR(100);

ALTER TABLE ecqm_artifact ADD COLUMN ecqm_title VARCHAR(200);
ALTER TABLE ecqm_artifact ADD COLUMN endorser VARCHAR(200);
ALTER TABLE ecqm_artifact ADD COLUMN endorsement_id VARCHAR(100);
ALTER TABLE ecqm_artifact ADD COLUMN risk_adjustment_description TEXT;
