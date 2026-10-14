-- PAT-259: composite measures completed (MADiE gap item 16).
--   component_weights  JSON object { "<component measure id>": weight } for the `weighted` composite scoring
--                      method (QM IG cqfm-weight). Nullable; absent = every component weighs 1.
-- composite_scoring / component_measure_ids (V5) are reused; the scoring method now also accepts
-- all-or-nothing and weighted (validated in MeasureDefinitionService.validateComposite).

ALTER TABLE measure_definition ADD COLUMN component_weights TEXT;
