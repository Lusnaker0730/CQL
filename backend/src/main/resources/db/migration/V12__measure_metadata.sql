-- Enhanced Measure Metadata
ALTER TABLE measure_definition ADD COLUMN rationale TEXT;
ALTER TABLE measure_definition ADD COLUMN clinical_guidance TEXT;
ALTER TABLE measure_definition ADD COLUMN steward VARCHAR(500);
ALTER TABLE measure_definition ADD COLUMN developers TEXT;
ALTER TABLE measure_definition ADD COLUMN measure_references TEXT;
ALTER TABLE measure_definition ADD COLUMN disclaimer TEXT;
ALTER TABLE measure_definition ADD COLUMN copyright TEXT;
ALTER TABLE measure_definition ADD COLUMN measure_set VARCHAR(200);
ALTER TABLE measure_definition ADD COLUMN supplemental_data_guidance TEXT;
ALTER TABLE measure_definition ADD COLUMN risk_adjustment_description TEXT;
ALTER TABLE measure_definition ADD COLUMN risk_adjustments TEXT;
ALTER TABLE measure_definition ADD COLUMN supplemental_data TEXT;
