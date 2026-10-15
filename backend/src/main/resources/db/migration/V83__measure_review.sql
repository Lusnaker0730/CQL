-- PAT-262: measure review — assigned reviewers with per-reviewer decisions, and threaded review
-- comments (MADiE gap item 8b; MADiE's MeasureReview {reviewers[], comment[] with replies}).
--
-- measure_reviewer: who was asked to review a measure and what they decided in the current review
--   round (pending / approved / changes-requested). submit-for-review resets every decision to
--   pending; a changes-requested decision returns the measure to draft (the author cannot edit a
--   measure in review, BUG-147). When a measure has assigned reviewers, only they may approve or
--   reject it (the author is still blocked by four-eyes).
-- measure_review_comment: one row per comment; parent_id = the thread's root comment (replies are
--   flattened to one level). section / sub_section anchor a thread to a part of the measure
--   (MADiE mainSection / subSection: e.g. "populations" / "<groupId>:numerator", "cql", "metadata").
--   resolved is kept on the root.
-- No RLS: like measure_definition / measure_set these tables carry no PHI; tenant scoping is by
-- query (tenant_id on every row, every repository method tenant-scoped).
CREATE TABLE measure_reviewer (
    id BIGSERIAL PRIMARY KEY,
    measure_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    assigned_by VARCHAR(100),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decision VARCHAR(20) NOT NULL DEFAULT 'pending',
    decision_comment VARCHAR(2000),
    decided_at TIMESTAMP,
    CONSTRAINT fk_measure_reviewer_measure FOREIGN KEY (measure_id) REFERENCES measure_definition(id) ON DELETE CASCADE,
    CONSTRAINT fk_measure_reviewer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uq_measure_reviewer UNIQUE (measure_id, username),
    CONSTRAINT chk_measure_reviewer_decision CHECK (decision IN ('pending', 'approved', 'changes-requested'))
);
CREATE INDEX idx_measure_reviewer_measure ON measure_reviewer(measure_id);
CREATE INDEX idx_measure_reviewer_user ON measure_reviewer(tenant_id, username);

CREATE TABLE measure_review_comment (
    id BIGSERIAL PRIMARY KEY,
    measure_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    author VARCHAR(100) NOT NULL,
    section VARCHAR(100),
    sub_section VARCHAR(200),
    body TEXT NOT NULL,
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_measure_review_comment_measure FOREIGN KEY (measure_id) REFERENCES measure_definition(id) ON DELETE CASCADE,
    CONSTRAINT fk_measure_review_comment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_measure_review_comment_parent FOREIGN KEY (parent_id) REFERENCES measure_review_comment(id) ON DELETE CASCADE
);
CREATE INDEX idx_measure_review_comment_measure ON measure_review_comment(measure_id);
CREATE INDEX idx_measure_review_comment_parent ON measure_review_comment(parent_id);
