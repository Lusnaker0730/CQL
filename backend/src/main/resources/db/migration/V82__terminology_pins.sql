-- PAT-260: terminology pinning (MADiE gap item 13 — MADiE pins a VSAC manifest; here the pins are per value set).
--   measure_definition.value_set_versions  JSON { "<value set url>": "<version>" } — the version the measure must
--                                          evaluate with when its CQL names none. Logic (BUG-147): editable in draft only.
--   measure_report.terminology_versions    JSON { "<value set url>": "<version used>" | "external" } — provenance of
--                                          one evaluation: which platform value set version each reference resolved to.
ALTER TABLE measure_definition ADD COLUMN value_set_versions TEXT;
ALTER TABLE measure_report ADD COLUMN terminology_versions TEXT;
