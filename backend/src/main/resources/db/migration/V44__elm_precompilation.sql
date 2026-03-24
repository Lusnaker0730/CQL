-- V44: Add elm_json to measure_definition for pre-compiled ELM caching
-- cql_library already has elm_json (V4), cds_artifact generates CQL dynamically so skip for now

ALTER TABLE measure_definition ADD COLUMN elm_json TEXT;
