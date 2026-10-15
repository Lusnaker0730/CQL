package com.cqlplatform.controller;

import com.cqlplatform.model.measure.MeasureReview;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.measure.MeasureReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT-262 — assigned reviewers, decisions and comment threads of one measure. Removals are POSTs
 * (the whole reviewer set is replaced with PUT): {@code DELETE /api/measures/**} is admin-only.
 */
@RestController
@RequestMapping("/api/measures/{id}/review")
@RequiredArgsConstructor
@Tag(name = "Measure Review", description = "PAT-262: reviewers, decisions and comment threads")
public class MeasureReviewController {

    private final MeasureReviewService reviewService;
    private final OwnershipVerifier ownershipVerifier;

    @GetMapping
    @Operation(summary = "The review of a measure", description = "Reviewers with their decisions, comment threads, and what the caller may do")
    public ResponseEntity<MeasureReview> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.getReview(id, ownershipVerifier.getCurrentUsername()));
    }

    @PutMapping("/reviewers")
    @Operation(summary = "Set the reviewers", description = "Owner or admin. The complete set: new names are assigned (and the measure shared with them), missing ones removed. The author may not be a reviewer (four-eyes)")
    public ResponseEntity<MeasureReview> setReviewers(@PathVariable Long id, @Valid @RequestBody MeasureReview.ReviewersRequest request) {
        return ResponseEntity.ok(reviewService.setReviewers(id, request.usernames(), ownershipVerifier.getCurrentUsername()));
    }

    @PostMapping("/decision")
    @Operation(summary = "Record a reviewer decision", description = "Assigned reviewers, while the measure is in review: approved, or changes-requested (returns the measure to draft)")
    public ResponseEntity<MeasureReview> decide(@PathVariable Long id, @Valid @RequestBody MeasureReview.DecisionRequest request) {
        return ResponseEntity.ok(reviewService.decide(id, request.decision(), request.comment(), ownershipVerifier.getCurrentUsername()));
    }

    @PostMapping("/comments")
    @Operation(summary = "Add a comment", description = "Owner, shared users, assigned reviewers or admin. parentId makes it a reply (threads are one level deep); section / subSection anchor a new thread")
    public ResponseEntity<MeasureReview.Comment> addComment(@PathVariable Long id, @Valid @RequestBody MeasureReview.CommentRequest request) {
        return ResponseEntity.ok(reviewService.addComment(id, request, ownershipVerifier.getCurrentUsername()));
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "Edit a comment", description = "Author only; the comment is marked edited")
    public ResponseEntity<MeasureReview.Comment> editComment(@PathVariable Long id, @PathVariable Long commentId,
                                                             @Valid @RequestBody MeasureReview.CommentRequest request) {
        return ResponseEntity.ok(reviewService.editComment(id, commentId, request.body(), ownershipVerifier.getCurrentUsername()));
    }

    @PostMapping("/comments/{commentId}/resolve")
    @Operation(summary = "Resolve a thread")
    public ResponseEntity<MeasureReview.Comment> resolve(@PathVariable Long id, @PathVariable Long commentId) {
        return ResponseEntity.ok(reviewService.setResolved(id, commentId, true, ownershipVerifier.getCurrentUsername()));
    }

    @PostMapping("/comments/{commentId}/unresolve")
    @Operation(summary = "Reopen a thread")
    public ResponseEntity<MeasureReview.Comment> unresolve(@PathVariable Long id, @PathVariable Long commentId) {
        return ResponseEntity.ok(reviewService.setResolved(id, commentId, false, ownershipVerifier.getCurrentUsername()));
    }
}
