-- V1: Initial schema - CDS Services, Users
-- Supports PostgreSQL (production) and H2 (dev)

-- CDS Service Configuration
CREATE TABLE IF NOT EXISTS cds_service_config (
    id              VARCHAR(100)    NOT NULL PRIMARY KEY,
    hook            VARCHAR(50)     NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000),
    cql_content     TEXT,
    cql_library_id  VARCHAR(100),
    default_indicator VARCHAR(20),
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- CDS Service Prefetch Items
CREATE TABLE IF NOT EXISTS cds_service_prefetch (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_id      VARCHAR(100)    NOT NULL,
    prefetch_key    VARCHAR(100)    NOT NULL,
    query           VARCHAR(500)    NOT NULL,
    CONSTRAINT fk_prefetch_service FOREIGN KEY (service_id) REFERENCES cds_service_config(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_prefetch_service_id ON cds_service_prefetch(service_id);

-- Application Users
CREATE TABLE IF NOT EXISTS app_user (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    password        VARCHAR(255)    NOT NULL,
    email           VARCHAR(100),
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_username ON app_user(username);
