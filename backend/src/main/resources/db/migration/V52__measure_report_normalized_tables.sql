-- Phase 1 of code-review #6 normalization (ADR-001).
-- New child tables for measure_report — groups, populations, stratifiers, stratifier populations.
-- Consumers continue reading result_json for now; dual-write populates these tables on new saves
-- and a one-shot startup backfill fills them for existing rows.
-- Phase 2 (consumer migration) lands in separate PRs.

-- ── measure_report_group ────────────────────────────────────────────────
CREATE TABLE measure_report_group (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_report_id BIGINT NOT NULL,
    group_id VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    measure_score DOUBLE PRECISION,
    measure_score_unit VARCHAR(100),
    total_patients INTEGER,
    -- Flattened ObservationStatistics (1:1 with group, CV measures only)
    obs_aggregate_method VARCHAR(50),
    obs_aggregate_value DOUBLE PRECISION,
    obs_count INTEGER,
    obs_minimum DOUBLE PRECISION,
    obs_maximum DOUBLE PRECISION,
    obs_average DOUBLE PRECISION,
    obs_median DOUBLE PRECISION,
    obs_unit VARCHAR(100),
    ordinal INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_mrg_report FOREIGN KEY (measure_report_id)
        REFERENCES measure_report(id) ON DELETE CASCADE,
    CONSTRAINT chk_mrg_score_range
        CHECK (measure_score IS NULL OR (measure_score >= 0 AND measure_score <= 10000)),
    CONSTRAINT chk_mrg_total_patients_nonneg
        CHECK (total_patients IS NULL OR total_patients >= 0)
);

CREATE INDEX idx_mrg_report ON measure_report_group (measure_report_id);
CREATE INDEX idx_mrg_report_ordinal ON measure_report_group (measure_report_id, ordinal);

-- ── measure_report_population ───────────────────────────────────────────
CREATE TABLE measure_report_population (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_report_group_id BIGINT NOT NULL,
    population_type VARCHAR(50) NOT NULL,  -- initial-population / numerator / denominator / measure-population / exclusion / exception
    population_id VARCHAR(200) NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    subject_ids_json TEXT,  -- low-cardinality patient-id list; kept as JSON since rarely queried individually
    ordinal INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_mrp_group FOREIGN KEY (measure_report_group_id)
        REFERENCES measure_report_group(id) ON DELETE CASCADE,
    CONSTRAINT chk_mrp_count_nonneg CHECK (count >= 0)
);

CREATE INDEX idx_mrp_group ON measure_report_population (measure_report_group_id);
CREATE INDEX idx_mrp_group_type ON measure_report_population (measure_report_group_id, population_type);
-- Dashboard use case: "find reports where denominator > 0 and numerator < denominator / 2"
CREATE INDEX idx_mrp_type_count ON measure_report_population (population_type, count);

-- ── measure_report_stratifier ───────────────────────────────────────────
CREATE TABLE measure_report_stratifier (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_report_group_id BIGINT NOT NULL,
    strata_id VARCHAR(200) NOT NULL,
    strata_value VARCHAR(500),
    measure_score DOUBLE PRECISION,
    ordinal INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_mrs_group FOREIGN KEY (measure_report_group_id)
        REFERENCES measure_report_group(id) ON DELETE CASCADE,
    CONSTRAINT chk_mrs_score_range
        CHECK (measure_score IS NULL OR (measure_score >= 0 AND measure_score <= 10000))
);

CREATE INDEX idx_mrs_group ON measure_report_stratifier (measure_report_group_id);

-- ── measure_report_stratifier_population ────────────────────────────────
CREATE TABLE measure_report_stratifier_population (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measure_report_stratifier_id BIGINT NOT NULL,
    population_type VARCHAR(50) NOT NULL,
    population_id VARCHAR(200) NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    ordinal INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_mrsp_strat FOREIGN KEY (measure_report_stratifier_id)
        REFERENCES measure_report_stratifier(id) ON DELETE CASCADE,
    CONSTRAINT chk_mrsp_count_nonneg CHECK (count >= 0)
);

CREATE INDEX idx_mrsp_strat ON measure_report_stratifier_population (measure_report_stratifier_id);
