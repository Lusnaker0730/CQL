package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReviewCommentEntity;
import com.cqlplatform.entity.MeasureReviewerEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.MeasureReview;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReviewCommentRepository;
import com.cqlplatform.repository.MeasureReviewerRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PAT-262: reviewers, decisions and comment threads — the rules, with repositories mocked. */
@ExtendWith(MockitoExtension.class)
class MeasureReviewServiceTest {

    private static final Long TENANT = 3L;

    @Mock private MeasureDefinitionRepository measureRepository;
    @Mock private MeasureReviewerRepository reviewerRepository;
    @Mock private MeasureReviewCommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MeasureAuditRepository auditRepository;
    @Mock private NotificationService notificationService;
    @Mock private MeasureDefinitionService definitionService;
    @Mock private OwnershipVerifier ownershipVerifier;

    private MeasureReviewService service;
    private MeasureDefinitionEntity measure;
    private final List<MeasureReviewerEntity> reviewers = new ArrayList<>();
    private final List<MeasureReviewCommentEntity> comments = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        service = new MeasureReviewService(measureRepository, reviewerRepository, commentRepository, userRepository,
                auditRepository, notificationService, definitionService, ownershipVerifier);
        service.setFourEyes(true);
        measure = MeasureDefinitionEntity.builder().id(10L).tenantId(TENANT).name("HbA1c").version("1.0.0")
                .status("in-review").ownerUsername("alice").createdBy("alice").accessLevel("private")
                .sharedWithList(new ArrayList<>(List.of("sam"))).build();
        lenient().when(definitionService.effectiveTenantId()).thenReturn(TENANT);
        lenient().when(measureRepository.findByIdAndTenantId(10L, TENANT)).thenReturn(Optional.of(measure));
        lenient().when(ownershipVerifier.isAdmin()).thenReturn(false);
        lenient().when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // in-memory reviewer / comment "tables"
        lenient().when(reviewerRepository.findByMeasureIdAndTenantIdOrderByAssignedAtAsc(10L, TENANT)).thenAnswer(inv -> new ArrayList<>(reviewers));
        lenient().when(reviewerRepository.findByMeasureIdAndTenantIdAndUsername(eq(10L), eq(TENANT), anyString()))
                .thenAnswer(inv -> reviewers.stream().filter(r -> r.getUsername().equals(inv.getArgument(2))).findFirst());
        lenient().when(reviewerRepository.existsByMeasureIdAndTenantIdAndUsername(eq(10L), eq(TENANT), anyString()))
                .thenAnswer(inv -> reviewers.stream().anyMatch(r -> r.getUsername().equals(inv.getArgument(2))));
        lenient().when(reviewerRepository.save(any())).thenAnswer(inv -> {
            MeasureReviewerEntity r = inv.getArgument(0);
            if (r.getId() == null) { r.setId(ids.incrementAndGet()); r.setAssignedAt(LocalDateTime.now()); reviewers.add(r); }
            return r;
        });
        lenient().doAnswer(inv -> reviewers.remove(inv.<MeasureReviewerEntity>getArgument(0))).when(reviewerRepository).delete(any());
        lenient().when(commentRepository.findByMeasureIdAndTenantIdOrderByCreatedAtAsc(10L, TENANT)).thenAnswer(inv -> new ArrayList<>(comments));
        lenient().when(commentRepository.findByIdAndMeasureIdAndTenantId(any(), eq(10L), eq(TENANT)))
                .thenAnswer(inv -> comments.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        lenient().when(commentRepository.save(any())).thenAnswer(inv -> {
            MeasureReviewCommentEntity c = inv.getArgument(0);
            if (c.getId() == null) { c.setId(ids.incrementAndGet()); c.setCreatedAt(LocalDateTime.now()); comments.add(c); }
            return c;
        });
        lenient().when(userRepository.findByUsername(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return switch (name) {
                case "bob", "alice", "sam" -> Optional.of(UserEntity.builder().username(name).tenantId(TENANT).build());
                case "carol" -> Optional.of(UserEntity.builder().username(name).tenantId(99L).build());
                default -> Optional.empty();
            };
        });
    }

    private MeasureReviewerEntity reviewer(String name, String decision) {
        MeasureReviewerEntity r = MeasureReviewerEntity.builder().id(ids.incrementAndGet()).measureId(10L).tenantId(TENANT)
                .username(name).decision(decision).assignedAt(LocalDateTime.now()).build();
        reviewers.add(r);
        return r;
    }

    private MeasureReviewCommentEntity comment(String author, Long parentId, boolean resolved) {
        MeasureReviewCommentEntity c = MeasureReviewCommentEntity.builder().id(ids.incrementAndGet()).measureId(10L).tenantId(TENANT)
                .author(author).parentId(parentId).body("…").resolved(resolved).createdAt(LocalDateTime.now()).build();
        comments.add(c);
        return c;
    }

    // ------------------------------------------------------------------ reviewers

    @Test
    void owner_assignsReviewers_sharesTheMeasureWithThem_auditsAndNotifies_andRemovesTheOnesLeftOut() {
        reviewer("old", MeasureReviewerEntity.PENDING);

        MeasureReview review = service.setReviewers(10L, List.of(" bob ", "bob"), "alice");

        assertThat(review.getReviewers()).extracting(MeasureReview.Reviewer::getUsername).containsExactly("bob");
        assertThat(review.getReviewers().get(0).getDecision()).isEqualTo("pending");
        verify(definitionService).ensureSharedWith(10L, "bob", "alice");
        verify(notificationService).notifyReviewAssigned("alice", "bob", "HbA1c", 10L);
        ArgumentCaptor<MeasureAuditEntity> audits = ArgumentCaptor.forClass(MeasureAuditEntity.class);
        verify(auditRepository, org.mockito.Mockito.times(2)).save(audits.capture());
        assertThat(audits.getAllValues()).extracting(MeasureAuditEntity::getAction).containsExactly("UNASSIGN_REVIEWER", "ASSIGN_REVIEWER");
        assertThat(review.getSummary().getReviewers()).isEqualTo(1);
    }

    @Test
    void reviewersMustBeUsersOfThisOrganisation_andNotTheAuthor() {
        assertThatThrownBy(() -> service.setReviewers(10L, List.of("ghost", "carol", "alice"), "alice"))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getDetails())
                        .anyMatch(d -> d.contains("'ghost'") && d.contains("not a user"))
                        .anyMatch(d -> d.contains("'carol'") && d.contains("organisation"))
                        .anyMatch(d -> d.contains("'alice'") && d.contains("four-eyes")));
        verify(reviewerRepository, never()).save(any());

        service.setFourEyes(false);
        assertThat(service.setReviewers(10L, List.of("alice"), "alice").getReviewers()).hasSize(1);
    }

    @Test
    void onlyTheOwnerOrAnAdminAssigns() {
        assertThatThrownBy(() -> service.setReviewers(10L, List.of("bob"), "sam")).isInstanceOf(AccessDeniedException.class);

        when(ownershipVerifier.isAdmin()).thenReturn(true);
        assertThat(service.setReviewers(10L, List.of("bob"), "root").getReviewers()).hasSize(1);
    }

    // ------------------------------------------------------------------ decisions

    @Test
    void assignedReviewer_approves_whileInReview() {
        reviewer("bob", MeasureReviewerEntity.PENDING);

        MeasureReview review = service.decide(10L, " Approved ", "fine by me", "bob");

        assertThat(review.getReviewers().get(0).getDecision()).isEqualTo("approved");
        assertThat(review.getReviewers().get(0).getDecisionComment()).isEqualTo("fine by me");
        assertThat(review.isReviewer()).isTrue();
        assertThat(review.getMyDecision()).isEqualTo("approved");
        assertThat(review.getSummary().getApproved()).isEqualTo(1);
        verify(notificationService).notifyReviewDecision("bob", "alice", "HbA1c", 10L, "approved", "fine by me");
        verify(definitionService, never()).rejectMeasure(any(), any(), any());
        assertThat(review.getStatus()).isEqualTo("in-review");
    }

    @Test
    void changesRequested_sendsTheMeasureBackToDraft_throughTheRejectFlow() {
        reviewer("bob", MeasureReviewerEntity.PENDING);

        service.decide(10L, "changes-requested", "numerator misses exclusions", "bob");

        verify(definitionService).rejectMeasure(10L, "numerator misses exclusions", "bob");
        assertThat(reviewers.get(0).getDecision()).isEqualTo("changes-requested");
    }

    @Test
    void decisions_needAnAssignedReviewer_aValidValue_andAMeasureInReview() {
        assertThatThrownBy(() -> service.decide(10L, "approved", null, "bob")).isInstanceOf(AccessDeniedException.class);
        reviewer("bob", MeasureReviewerEntity.PENDING);
        assertThatThrownBy(() -> service.decide(10L, "maybe", null, "bob")).isInstanceOf(ValidationException.class);
        measure.setStatus("draft");
        assertThatThrownBy(() -> service.decide(10L, "approved", null, "bob")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("in review");
        assertThat(reviewers.get(0).getDecision()).isEqualTo("pending");
    }

    // ------------------------------------------------------------------ comments

    @Test
    void participantsComment_repliesAreFlattenedToTheRoot_andReopenAResolvedThread() {
        reviewer("bob", MeasureReviewerEntity.PENDING);

        MeasureReview.Comment root = service.addComment(10L, new MeasureReview.CommentRequest("Why 18?", null, "populations", "g1:initial-population"), "alice");
        MeasureReview.Comment reply = service.addComment(10L, new MeasureReview.CommentRequest("Guideline says adults", root.getId(), null, null), "bob");
        service.setResolved(10L, reply.getId(), true, "sam");
        assertThat(comments.get(0).isResolved()).isTrue();
        MeasureReview.Comment replyToReply = service.addComment(10L, new MeasureReview.CommentRequest("Then say so in the guidance", reply.getId(), null, null), "sam");

        assertThat(reply.getParentId()).isEqualTo(root.getId());
        assertThat(replyToReply.getParentId()).as("one level deep").isEqualTo(root.getId());
        assertThat(replyToReply.getSection()).isEqualTo("populations");
        assertThat(comments.get(0).isResolved()).as("a new reply reopens the thread").isFalse();

        MeasureReview review = service.getReview(10L, "bob");
        assertThat(review.getComments()).hasSize(1);
        assertThat(review.getComments().get(0).getReplies()).hasSize(2);
        assertThat(review.getSummary().getOpenThreads()).isEqualTo(1);
        assertThat(review.getSummary().getComments()).isEqualTo(3);
        assertThat(review.isCanComment()).isTrue();
        assertThat(review.isCanAssign()).isFalse();

        // the owner, the thread's authors and the reviewers hear about it — never the writer
        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        verify(notificationService, org.mockito.Mockito.times(3)).notifyReviewComment(anyString(), recipients.capture(), eq("HbA1c"), eq(10L), anyString());
        assertThat(recipients.getAllValues().get(0)).containsExactly("bob");            // alice's root: owner is alice herself
        assertThat(recipients.getAllValues().get(1)).containsExactly("alice");          // bob's reply: owner + root author
        assertThat(recipients.getAllValues().get(2)).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void strangersCannotComment_andOnlyTheAuthorEdits() {
        assertThatThrownBy(() -> service.addComment(10L, new MeasureReview.CommentRequest("hi", null, null, null), "stranger"))
                .isInstanceOf(AccessDeniedException.class);
        MeasureReviewCommentEntity c = comment("alice", null, false);
        assertThatThrownBy(() -> service.editComment(10L, c.getId(), "edited", "sam")).isInstanceOf(AccessDeniedException.class);

        MeasureReview.Comment edited = service.editComment(10L, c.getId(), " edited ", "alice");
        assertThat(edited.getBody()).isEqualTo("edited");
        assertThat(edited.isEdited()).isTrue();
    }

    @Test
    void readingTheReview_needsAccessToTheMeasure_publicMeasuresAreReadableByAnyone() {
        assertThatThrownBy(() -> service.getReview(10L, "stranger")).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getReview(10L, "sam").isCanComment()).isTrue();
        measure.setAccessLevel("public");
        MeasureReview review = service.getReview(10L, "stranger");
        assertThat(review.isCanComment()).isFalse();
        assertThat(review.isCanAssign()).isFalse();
        assertThat(service.isParticipant(10L, "sam")).isTrue();
        assertThat(service.isParticipant(10L, "stranger")).isFalse();
    }
}
