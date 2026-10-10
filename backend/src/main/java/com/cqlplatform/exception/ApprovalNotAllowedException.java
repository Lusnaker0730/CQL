package com.cqlplatform.exception;

/**
 * PAT-249 — four-eyes principle: the person who owns a measure or submitted it for review may
 * not be the one who approves it. Mapped to HTTP 403 {@code Approval Not Allowed}. Reviewer
 * membership is still checked first (a non-reviewer gets the ordinary refusal), and the rule can
 * be switched off for single-person installations with {@code measure.review.four-eyes=false}.
 */
public class ApprovalNotAllowedException extends RuntimeException {

    public ApprovalNotAllowedException(String message) {
        super(message);
    }
}
