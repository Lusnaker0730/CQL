-- Rollback V10: Fix Library ID Type
-- Revert library_id columns from VARCHAR(255) back to BIGINT.
-- WARNING: Will fail if any existing library_id is not a valid integer.

ALTER TABLE user_favorites ALTER COLUMN library_id TYPE BIGINT USING library_id::BIGINT;
ALTER TABLE user_recent_libraries ALTER COLUMN library_id TYPE BIGINT USING library_id::BIGINT;
