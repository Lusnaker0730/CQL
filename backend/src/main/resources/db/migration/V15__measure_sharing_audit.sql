-- Measure sharing & ownership
ALTER TABLE measure_definition ADD COLUMN IF NOT EXISTS owner_username VARCHAR(100);
ALTER TABLE measure_definition ADD COLUMN IF NOT EXISTS shared_with TEXT DEFAULT '[]';
ALTER TABLE measure_definition ADD COLUMN IF NOT EXISTS access_level VARCHAR(20) DEFAULT 'private';

CREATE INDEX IF NOT EXISTS idx_measure_def_owner ON measure_definition(owner_username);
CREATE INDEX IF NOT EXISTS idx_measure_def_access ON measure_definition(access_level);

-- Measure audit trail
CREATE TABLE IF NOT EXISTS measure_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100),
    details TEXT,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_measure_audit_measure ON measure_audit(measure_id);
CREATE INDEX IF NOT EXISTS idx_measure_audit_action ON measure_audit(action);
