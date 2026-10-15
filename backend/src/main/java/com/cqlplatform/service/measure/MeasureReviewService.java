package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReviewCommentEntity;
import com.cqlplatform.entity.MeasureReviewerEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.MeasureReview;
import com.cqlplatform.model.measure.MeasureStatusConstants;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReviewCommentRepository;
import com.cqlplatform.repository.MeasureReviewerRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.NotificationService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * PAT-262 — assigned reviewers, their decisions, and threaded review comments (MADiE gap item 8b).
 *
 * <ul>
 *   <li><b>Reviewers</b> are assigned by the owner (or an admin). Assigning shares the measure with
 *       the reviewer so they can open it. While a measure has assigned reviewers, only they may
 *       approve or reject it ({@link MeasureDefinitionService#checkReviewer}); the author stays
 *       blocked by four-eyes, and may not be assigned.</li>
 *   <li><b>Decisions</b> are recorded while the measure is in review. {@code changes-requested}
 *       returns it to draft at once (the author cannot edit a measure in review, BUG-147) — the
 *       existing reject flow, with the reviewer's comment as the reason. {@code approved} is the
 *       reviewer's verdict; the status change is still the explicit approve. Submit-for-review
 *       starts a new round: every decision goes back to pending.</li>
 *   <li><b>Comments</b> are one level deep: a reply carries the root's id. Every participant
 *       (owner, shared user, assigned reviewer, admin) may comment and resolve; only the author
 *       edits their own comment.</li>
 * </ul>
 * Everything is tenant-scoped through the measure; cross-tenant ids are 404, like elsewhere.
 */
@Service
@Slf4j
public class MeasureReviewService {

    private final MeasureDefinitionRepository measureRepository;
    private final MeasureReviewerRepository reviewerRepository;
    private final MeasureReviewCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final MeasureAuditRepository auditRepository;
    private final NotificationService notificationService;
    private final MeasureDefinitionService definitionService;
    private final OwnershipVerifier ownershipVerifier;

    /** {@code measure.review.four-eyes}: the author may not be assigned as a reviewer of their own measure. */
    @Setter
    @Value("${measure.review.four-eyes:true}")
    private boolean fourEyes = true;

    public MeasureReviewService(MeasureDefinitionRepository measureRepository,
                                MeasureReviewerRepository reviewerRepository,
                                MeasureReviewCommentRepository commentRepository,
                                UserRepository userRepository,
                                MeasureAuditRepository auditRepository,
                                NotificationService notificationService,
                                MeasureDefinitionService definitionService,
                                OwnershipVerifier ownershipVerifier) {
        this.measureRepository = measureRepository;
        this.reviewerRepository = reviewerRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.notificationService = notificationService;
        this.definitionService = definitionService;
        this.ownershipVerifier = ownershipVerifier;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public MeasureReview getReview(Long measureId, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        if (!canRead(measure, currentUser)) {
            throw new AccessDeniedException("You do not have access to this measure");
        }
        return build(measure, currentUser);
    }

    /** Owner, shared user, assigned reviewer or admin — the people who take part in a review. */
    @Transactional(readOnly = true)
    public boolean isParticipant(Long measureId, String currentUser) {
        return measureRepository.findByIdAndTenantId(measureId, definitionService.effectiveTenantId())
                .map(m -> isParticipant(m, currentUser)).orElse(false);
    }

    // ------------------------------------------------------------------ reviewers

    /** Replaces the set of reviewers: new names are assigned (and the measure shared with them), missing ones removed. */
    @Transactional
    public MeasureReview setReviewers(Long measureId, List<String> usernames, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        if (!canAssign(measure, currentUser)) {
            throw new AccessDeniedException("Only the owner or an admin can assign reviewers");
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String name : usernames == null ? List.<String>of() : usernames) {
            if (name != null && !name.isBlank()) wanted.add(name.trim());
        }
        List<String> problems = new ArrayList<>();
        String author = authorOf(measure);
        for (String name : wanted) {
            Optional<UserEntity> user = userRepository.findByUsername(name);
            if (user.isEmpty()) {
                problems.add("'" + name + "' is not a user of this installation");
            } else if (user.get().getTenantId() != null && !Objects.equals(user.get().getTenantId(), measure.getTenantId())) {
                problems.add("'" + name + "' is not a user of this organisation");
            } else if (fourEyes && name.equals(author)) {
                problems.add("'" + name + "' is the measure's author and may not review it (four-eyes)");
            }
        }
        if (!problems.isEmpty()) {
            throw new ValidationException("Reviewers could not be assigned", problems);
        }

        List<MeasureReviewerEntity> current = reviewerRepository
                .findByMeasureIdAndTenantIdOrderByAssignedAtAsc(measure.getId(), measure.getTenantId());
        for (MeasureReviewerEntity existing : current) {
            if (!wanted.contains(existing.getUsername())) {
                reviewerRepository.delete(existing);
                audit(measure.getId(), "UNASSIGN_REVIEWER", currentUser, "Reviewer removed: " + existing.getUsername(), existing.getUsername(), null);
                log.info("Measure {}: reviewer {} removed by {}", measure.getId(), existing.getUsername(), currentUser);
            }
        }
        Set<String> already = new LinkedHashSet<>();
        current.forEach(r -> already.add(r.getUsername()));
        for (String name : wanted) {
            if (already.contains(name)) continue;
            reviewerRepository.save(MeasureReviewerEntity.builder()
                    .measureId(measure.getId()).tenantId(measure.getTenantId())
                    .username(name).assignedBy(currentUser).build());
            audit(measure.getId(), "ASSIGN_REVIEWER", currentUser, "Reviewer assigned: " + name, null, name);
            // the reviewer has to be able to open the measure
            definitionService.ensureSharedWith(measure.getId(), name, currentUser);
            notificationService.notifyReviewAssigned(currentUser, name, measure.getName(), measure.getId());
            log.info("Measure {}: reviewer {} assigned by {}", measure.getId(), name, currentUser);
        }
        return build(requireMeasure(measureId), currentUser);
    }

    // ------------------------------------------------------------------ decisions

    @Transactional
    public MeasureReview decide(Long measureId, String decision, String comment, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        MeasureReviewerEntity reviewer = reviewerRepository
                .findByMeasureIdAndTenantIdAndUsername(measure.getId(), measure.getTenantId(), currentUser)
                .orElseThrow(() -> new AccessDeniedException("Only an assigned reviewer can record a decision"));
        String normalized = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
        if (!MeasureReview.DECISIONS.contains(normalized)) {
            throw new ValidationException("decision must be one of " + MeasureReview.DECISIONS);
        }
        if (!MeasureStatusConstants.IN_REVIEW.equals(measure.getStatus())) {
            throw new ValidationException("Decisions are recorded while the measure is in review (current status: "
                    + measure.getStatus() + ")");
        }
        reviewer.setDecision(normalized);
        reviewer.setDecisionComment(comment != null && !comment.isBlank() ? comment.trim() : null);
        reviewer.setDecidedAt(LocalDateTime.now());
        reviewerRepository.save(reviewer);
        audit(measure.getId(), "REVIEW_DECISION", currentUser,
                "Reviewer decision: " + normalized + (reviewer.getDecisionComment() != null ? " — " + reviewer.getDecisionComment() : ""),
                null, normalized);
        notificationService.notifyReviewDecision(currentUser, measure.getOwnerUsername(), measure.getName(), measure.getId(),
                normalized, reviewer.getDecisionComment());
        log.info("Measure {}: reviewer {} decided {}", measure.getId(), currentUser, normalized);

        if (MeasureReview.CHANGES_REQUESTED.equals(normalized)) {
            // the author cannot edit a measure in review: send it back to draft, as a rejection
            definitionService.rejectMeasure(measure.getId(), reviewer.getDecisionComment() != null
                    ? reviewer.getDecisionComment() : "Changes requested by " + currentUser, currentUser);
        }
        return build(requireMeasure(measureId), currentUser);
    }

    // ------------------------------------------------------------------ comments

    @Transactional
    public MeasureReview.Comment addComment(Long measureId, MeasureReview.CommentRequest request, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        requireParticipant(measure, currentUser);
        Long rootId = null;
        MeasureReviewCommentEntity root = null;
        if (request.parentId() != null) {
            root = rootOf(requireComment(measure, request.parentId()), measure);
            rootId = root.getId();
        }
        MeasureReviewCommentEntity saved = commentRepository.save(MeasureReviewCommentEntity.builder()
                .measureId(measure.getId()).tenantId(measure.getTenantId())
                .parentId(rootId).author(currentUser)
                .section(root != null ? root.getSection() : blankToNull(request.section()))
                .subSection(root != null ? root.getSubSection() : blankToNull(request.subSection()))
                .body(request.body().trim())
                .build());
        if (root != null && root.isResolved()) {
            // a new reply reopens the thread
            root.setResolved(false);
            root.setResolvedBy(null);
            root.setResolvedAt(null);
            commentRepository.save(root);
        }
        notificationService.notifyReviewComment(currentUser, recipientsOf(measure, saved, root),
                measure.getName(), measure.getId(), saved.getBody());
        log.info("Measure {}: comment {} by {}{}", measure.getId(), saved.getId(), currentUser, rootId != null ? " (reply to " + rootId + ")" : "");
        return toComment(saved, List.of());
    }

    @Transactional
    public MeasureReview.Comment editComment(Long measureId, Long commentId, String body, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        MeasureReviewCommentEntity comment = requireComment(measure, commentId);
        if (!comment.getAuthor().equals(currentUser)) {
            throw new AccessDeniedException("Only the author can edit a comment");
        }
        if (body == null || body.isBlank()) throw new ValidationException("Comment body must not be blank");
        comment.setBody(body.trim());
        comment.setEdited(true);
        return toComment(commentRepository.save(comment), repliesOf(measure, comment));
    }

    /** Resolves / reopens the thread the comment belongs to. */
    @Transactional
    public MeasureReview.Comment setResolved(Long measureId, Long commentId, boolean resolved, String currentUser) {
        MeasureDefinitionEntity measure = requireMeasure(measureId);
        requireParticipant(measure, currentUser);
        MeasureReviewCommentEntity root = rootOf(requireComment(measure, commentId), measure);
        root.setResolved(resolved);
        root.setResolvedBy(resolved ? currentUser : null);
        root.setResolvedAt(resolved ? LocalDateTime.now() : null);
        root = commentRepository.save(root);
        log.info("Measure {}: thread {} {} by {}", measure.getId(), root.getId(), resolved ? "resolved" : "reopened", currentUser);
        return toComment(root, repliesOf(measure, root));
    }

    // ------------------------------------------------------------------ rules

    boolean isParticipant(MeasureDefinitionEntity measure, String user) {
        if (user == null) return false;
        if (ownershipVerifier.isAdmin()) return true;
        if (measure.getOwnerUsername() == null || measure.getOwnerUsername().equals(user)) return true;
        if (user.equals(measure.getCreatedBy())) return true;
        if (measure.getSharedWithList().contains(user)) return true;
        return reviewerRepository.existsByMeasureIdAndTenantIdAndUsername(measure.getId(), measure.getTenantId(), user);
    }

    private boolean canRead(MeasureDefinitionEntity measure, String user) {
        String access = measure.getAccessLevel();
        if ("public".equalsIgnoreCase(access) || "shared".equalsIgnoreCase(access)) return true;
        return isParticipant(measure, user);
    }

    private boolean canAssign(MeasureDefinitionEntity measure, String user) {
        return ownershipVerifier.isAdmin() || measure.getOwnerUsername() == null || measure.getOwnerUsername().equals(user);
    }

    private void requireParticipant(MeasureDefinitionEntity measure, String user) {
        if (!isParticipant(measure, user)) {
            throw new AccessDeniedException("Only the owner, shared users, assigned reviewers or an admin can take part in this review");
        }
    }

    private static String authorOf(MeasureDefinitionEntity measure) {
        return measure.getOwnerUsername() != null && !measure.getOwnerUsername().isBlank()
                ? measure.getOwnerUsername() : measure.getCreatedBy();
    }

    // ------------------------------------------------------------------ helpers

    private MeasureDefinitionEntity requireMeasure(Long id) {
        return measureRepository.findByIdAndTenantId(id, definitionService.effectiveTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Measure", id));
    }

    private MeasureReviewCommentEntity requireComment(MeasureDefinitionEntity measure, Long commentId) {
        return commentRepository.findByIdAndMeasureIdAndTenantId(commentId, measure.getId(), measure.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }

    private MeasureReviewCommentEntity rootOf(MeasureReviewCommentEntity comment, MeasureDefinitionEntity measure) {
        return comment.getParentId() == null ? comment : requireComment(measure, comment.getParentId());
    }

    private List<MeasureReviewCommentEntity> repliesOf(MeasureDefinitionEntity measure, MeasureReviewCommentEntity root) {
        return commentRepository.findByMeasureIdAndTenantIdOrderByCreatedAtAsc(measure.getId(), measure.getTenantId()).stream()
                .filter(c -> root.getId().equals(c.getParentId())).toList();
    }

    /** Owner, the thread's authors and the assigned reviewers — minus whoever is writing. */
    private List<String> recipientsOf(MeasureDefinitionEntity measure, MeasureReviewCommentEntity comment, MeasureReviewCommentEntity root) {
        Set<String> recipients = new LinkedHashSet<>();
        if (measure.getOwnerUsername() != null) recipients.add(measure.getOwnerUsername());
        if (root != null) {
            recipients.add(root.getAuthor());
            repliesOf(measure, root).forEach(r -> recipients.add(r.getAuthor()));
        }
        reviewerRepository.findByMeasureIdAndTenantIdOrderByAssignedAtAsc(measure.getId(), measure.getTenantId())
                .forEach(r -> recipients.add(r.getUsername()));
        recipients.remove(comment.getAuthor());
        return new ArrayList<>(recipients);
    }

    private MeasureReview build(MeasureDefinitionEntity measure, String currentUser) {
        List<MeasureReviewerEntity> reviewers = reviewerRepository
                .findByMeasureIdAndTenantIdOrderByAssignedAtAsc(measure.getId(), measure.getTenantId());
        List<MeasureReviewCommentEntity> all = commentRepository
                .findByMeasureIdAndTenantIdOrderByCreatedAtAsc(measure.getId(), measure.getTenantId());

        Map<Long, List<MeasureReviewCommentEntity>> replies = new LinkedHashMap<>();
        List<MeasureReviewCommentEntity> roots = new ArrayList<>();
        for (MeasureReviewCommentEntity c : all) {
            if (c.getParentId() == null) roots.add(c);
            else replies.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        List<MeasureReview.Comment> comments = roots.stream()
                .map(r -> toComment(r, replies.getOrDefault(r.getId(), List.of()))).toList();

        int approved = 0, changes = 0, pending = 0;
        String mine = null;
        for (MeasureReviewerEntity r : reviewers) {
            switch (r.getDecision()) {
                case MeasureReviewerEntity.APPROVED -> approved++;
                case MeasureReviewerEntity.CHANGES_REQUESTED -> changes++;
                default -> pending++;
            }
            if (r.getUsername().equals(currentUser)) mine = r.getDecision();
        }
        int open = (int) roots.stream().filter(r -> !r.isResolved()).count();

        return MeasureReview.builder()
                .measureId(measure.getId())
                .status(measure.getStatus())
                .reviewers(reviewers.stream().map(r -> MeasureReview.Reviewer.builder()
                        .username(r.getUsername()).assignedBy(r.getAssignedBy()).assignedAt(r.getAssignedAt())
                        .decision(r.getDecision()).decisionComment(r.getDecisionComment()).decidedAt(r.getDecidedAt())
                        .build()).toList())
                .comments(comments)
                .summary(MeasureReview.Summary.builder()
                        .reviewers(reviewers.size()).approved(approved).changesRequested(changes).pending(pending)
                        .openThreads(open).resolvedThreads(roots.size() - open).comments(all.size())
                        .build())
                .canAssign(canAssign(measure, currentUser))
                .canComment(isParticipant(measure, currentUser))
                .reviewer(mine != null)
                .myDecision(mine)
                .build();
    }

    private static MeasureReview.Comment toComment(MeasureReviewCommentEntity c, List<MeasureReviewCommentEntity> replies) {
        return MeasureReview.Comment.builder()
                .id(c.getId()).parentId(c.getParentId()).author(c.getAuthor())
                .section(c.getSection()).subSection(c.getSubSection()).body(c.getBody())
                .edited(c.isEdited()).resolved(c.isResolved()).resolvedBy(c.getResolvedBy()).resolvedAt(c.getResolvedAt())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .replies(replies.stream().map(r -> toComment(r, List.of())).toList())
                .build();
    }

    private void audit(Long measureId, String action, String by, String details, String oldValue, String newValue) {
        auditRepository.save(MeasureAuditEntity.builder()
                .measureId(measureId).action(action).performedBy(by).details(details)
                .oldValue(oldValue).newValue(newValue).build());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
