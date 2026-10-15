-- Rollback V83: drop the assigned reviewers and the review comment threads (PAT-262).
--
-- Reviewer assignments, their decisions and every review comment are lost; the measures
-- themselves (status, approvedBy, reviewComment) are untouched. Roll the application back to a
-- pre-PAT-262 image at the same time (ddl-auto: validate expects the tables).
DROP TABLE IF EXISTS measure_review_comment;
DROP TABLE IF EXISTS measure_reviewer;
