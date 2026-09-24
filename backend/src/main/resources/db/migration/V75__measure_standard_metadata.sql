-- PAT-236: standard FHIR Measure metadata the platform did not model before —
-- Measure.type[], Measure.definition[], Measure.clinicalRecommendationStatement,
-- Measure.effectivePeriod, Measure.approvalDate, Measure.lastReviewDate, Measure.experimental.
-- Both the published measure and the eCQM workspace artifact carry them (publish copies
-- artifact → measure). JSON lists follow the existing measure_definition convention
-- (developers, measure_references, risk_adjustments, supplemental_data).

ALTER TABLE measure_definition ADD COLUMN measure_types TEXT;
ALTER TABLE measure_definition ADD COLUMN definition_terms TEXT;
ALTER TABLE measure_definition ADD COLUMN clinical_recommendation_statement TEXT;
ALTER TABLE measure_definition ADD COLUMN effective_start DATE;
ALTER TABLE measure_definition ADD COLUMN effective_end DATE;
ALTER TABLE measure_definition ADD COLUMN approval_date DATE;
ALTER TABLE measure_definition ADD COLUMN last_review_date DATE;
ALTER TABLE measure_definition ADD COLUMN experimental BOOLEAN;

ALTER TABLE ecqm_artifact ADD COLUMN measure_types TEXT;
ALTER TABLE ecqm_artifact ADD COLUMN definition_terms TEXT;
ALTER TABLE ecqm_artifact ADD COLUMN clinical_recommendation_statement TEXT;
ALTER TABLE ecqm_artifact ADD COLUMN effective_start DATE;
ALTER TABLE ecqm_artifact ADD COLUMN effective_end DATE;
ALTER TABLE ecqm_artifact ADD COLUMN approval_date DATE;
ALTER TABLE ecqm_artifact ADD COLUMN last_review_date DATE;
ALTER TABLE ecqm_artifact ADD COLUMN experimental BOOLEAN;
