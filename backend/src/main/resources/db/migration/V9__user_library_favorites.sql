CREATE TABLE IF NOT EXISTS user_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    library_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_favorite UNIQUE (username, library_id)
);

CREATE TABLE IF NOT EXISTS user_recent_libraries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    library_id BIGINT NOT NULL,
    library_name VARCHAR(200),
    library_version VARCHAR(50),
    accessed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_recent UNIQUE (username, library_id)
);

CREATE INDEX idx_user_favorites_username ON user_favorites(username);
CREATE INDEX idx_user_recent_username ON user_recent_libraries(username);
