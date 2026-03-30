-- Change library_id from BIGINT to VARCHAR to match CQL library composite IDs (name-version)
ALTER TABLE user_favorites ALTER COLUMN library_id TYPE VARCHAR(255) USING CAST(library_id AS VARCHAR);
ALTER TABLE user_recent_libraries ALTER COLUMN library_id TYPE VARCHAR(255) USING CAST(library_id AS VARCHAR);
