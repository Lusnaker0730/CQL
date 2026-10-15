package com.cqlplatform.model.measure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * PAT-262 — the review of one measure as the UI shows it: who was asked to review and what they
 * decided, the comment threads, and what the caller may do. Mirrors MADiE's {@code MeasureReview}
 * ({@code reviewers[]}, {@code comment[]} with replies) plus per-reviewer decisions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureReview {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String CHANGES_REQUESTED = "changes-requested";
    public static final Set<String> DECISIONS = Set.of(APPROVED, CHANGES_REQUESTED);

    private Long measureId;
    private String status;
    private List<Reviewer> reviewers;
    /** Root comments in creation order, each with its replies. */
    private List<Comment> comments;
    private Summary summary;
    /** The caller may assign / remove reviewers (owner or admin). */
    private boolean canAssign;
    /** The caller may comment (owner, shared user, assigned reviewer or admin). */
    private boolean canComment;
    /** The caller is an assigned reviewer. */
    private boolean reviewer;
    /** The caller's decision when they are an assigned reviewer, else null. */
    private String myDecision;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reviewer {
        private String username;
        private String assignedBy;
        private LocalDateTime assignedAt;
        private String decision;
        private String decisionComment;
        private LocalDateTime decidedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comment {
        private Long id;
        private Long parentId;
        private String author;
        private String section;
        private String subSection;
        private String body;
        private boolean edited;
        private boolean resolved;
        private String resolvedBy;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<Comment> replies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int reviewers;
        private int approved;
        private int changesRequested;
        private int pending;
        private int openThreads;
        private int resolvedThreads;
        private int comments;
    }

    /** {@code PUT /review/reviewers}: the complete set of reviewers (missing ones are removed). */
    public record ReviewersRequest(@NotNull List<@NotBlank @Size(max = 100) String> usernames) {}

    /** {@code POST /review/decision}. */
    public record DecisionRequest(@NotBlank @Size(max = 20) String decision, @Size(max = 2000) String comment) {}

    /** {@code POST /review/comments} and {@code PUT /review/comments/{id}} (body only). */
    public record CommentRequest(@NotBlank @Size(max = 4000) String body, Long parentId,
                                 @Size(max = 100) String section, @Size(max = 200) String subSection) {}
}
