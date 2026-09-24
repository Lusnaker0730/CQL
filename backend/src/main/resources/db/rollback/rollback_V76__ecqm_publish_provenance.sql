-- Rollback V76: drop the eCQM publish provenance columns (PAT-238).
--
-- The measure page loses its "edited since publish" / "builder changed since publish" status and
-- re-publish no longer asks before overwriting measure-page edits. Roll the application back to a
-- pre-PAT-238 image at the same time (ddl-auto: validate expects the columns).

DROP INDEX IF EXISTS idx_ecqm_artifact_published_measure;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS published_content_hash;
ALTER TABLE ecqm_artifact DROP COLUMN IF EXISTS published_at;
