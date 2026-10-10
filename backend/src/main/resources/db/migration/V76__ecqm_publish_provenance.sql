-- PAT-238: what the eCQM builder last published onto its measure, so the measure page can link
-- back to the builder and both sides can tell whether the other changed since that publish.
--   published_at            when the artifact was last published
--   published_content_hash  SHA-256 of the published CQL + group definitions (normalised); a
--                           measure whose current content hashes differently was edited on the
--                           measure page after publish, and a re-publish would overwrite that.
-- NULL for artifacts never published, or published before this migration (no baseline: the
-- guard stays off until the next publish records one).

ALTER TABLE ecqm_artifact ADD COLUMN published_at TIMESTAMP;
ALTER TABLE ecqm_artifact ADD COLUMN published_content_hash VARCHAR(64);

CREATE INDEX idx_ecqm_artifact_published_measure ON ecqm_artifact (tenant_id, published_measure_id);
