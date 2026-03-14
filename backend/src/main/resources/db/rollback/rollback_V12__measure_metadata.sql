-- Rollback V12: Measure Metadata

ALTER TABLE measure_definition DROP COLUMN IF EXISTS rationale;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS clinical_guidance;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS steward;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS developers;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS measure_references;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS disclaimer;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS copyright;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS measure_set;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS supplemental_data_guidance;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS risk_adjustment_description;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS risk_adjustments;
ALTER TABLE measure_definition DROP COLUMN IF EXISTS supplemental_data;
