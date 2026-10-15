package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureAuditEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureReviewerEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ApprovalNotAllowedException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.ApprovalReadiness;
import com.cqlplatform.model.measure.MeasureReview;
import com.cqlplatform.repository.MeasureAuditRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureReviewerRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-262 on H2 with the real repositories and services: the JPA mappings, the modifying query
 * that resets decisions, and the hand-off between the review service and the workflow.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeasureReviewIntegrationTest {

    private static final Long TENANT = 1L;

    @Autowired private MeasureDefinitionRepository measures;
    @Autowired private MeasureReviewerRepository reviewers;
    @Autowired private MeasureAuditRepository audits;
    @Autowired private UserRepository users;
    @Autowired private MeasureReviewService review;
    @Autowired private MeasureDefinitionService definitions;

    private Long measureId;

    @BeforeEach
    void seed() {
        TenantContext.setCurrentTenantId(TENANT);
        user("rev-alice", TENANT);
        user("rev-bob", TENANT);
        user("rev-carol", 2L);
        measureId = measures.saveAndFlush(MeasureDefinitionEntity.builder()
                .name("rev-HbA1c").version("1.0.0").status("draft").scoringType("cohort")
                .cqlContent("library RevHbA1c version '1.0.0'\n\ndefine \"Initial Population\": true\n")
                .ownerUsername("rev-alice").createdBy("rev-alice").accessLevel("private")
                .sharedWithList(new ArrayList<>()).tenantId(TENANT).build()).getId();
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void user(String name, Long tenant) {
        if (users.findByUsername(name).isEmpty()) {
            users.saveAndFlush(UserEntity.builder().username(name).password("encoded").role(UserEntity.Role.USER)
                    .enabled(true).tenantId(tenant).build());
        }
    }

    @Test
    void assign_submit_decide_approve_withAssignedReviewersOnlyTheyApprove() {
        MeasureReview after = review.setReviewers(measureId, List.of("rev-bob"), "rev-alice");
        assertThat(after.getReviewers()).extracting(MeasureReview.Reviewer::getUsername).containsExactly("rev-bob");
        MeasureDefinitionEntity shared = measures.findById(measureId).orElseThrow();
        assertThat(shared.getSharedWithList()).as("assigning shares the measure").contains("rev-bob");
        assertThat(shared.getAccessLevel()).isEqualTo("shared");
        assertThat(audits.findByMeasureIdOrderByCreatedAtDesc(measureId)).extracting(MeasureAuditEntity::getAction)
                .contains("ASSIGN_REVIEWER", "SHARE");

        assertThatThrownBy(() -> review.setReviewers(measureId, List.of("rev-carol"), "rev-alice")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> review.setReviewers(measureId, List.of("rev-alice"), "rev-alice")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> review.decide(measureId, "approved", null, "rev-bob")).as("not in review yet").isInstanceOf(ValidationException.class);

        definitions.submitForReview(measureId, "rev-alice");
        assertThat(measures.findById(measureId).orElseThrow().getStatus()).isEqualTo("in-review");

        MeasureReview decided = review.decide(measureId, "approved", "looks right", "rev-bob");
        assertThat(decided.getMyDecision()).isEqualTo("approved");
        assertThat(decided.getSummary().getApproved()).isEqualTo(1);
        assertThat(audits.findByMeasureIdOrderByCreatedAtDesc(measureId).get(0).getAction()).isEqualTo("REVIEW_DECISION");

        // someone who is not an assigned reviewer may not approve; the assigned reviewer may
        assertThatThrownBy(() -> definitions.approveMeasure(measureId, "rev-erin")).isInstanceOf(ApprovalNotAllowedException.class)
                .hasMessageContaining("rev-bob");
        assertThat(definitions.approveMeasure(measureId, "rev-bob").getStatus()).isEqualTo("active");
    }

    @Test
    void changesRequested_returnsTheMeasureToDraft_andResubmittingResetsEveryDecision() {
        review.setReviewers(measureId, List.of("rev-bob"), "rev-alice");
        definitions.submitForReview(measureId, "rev-alice");

        review.decide(measureId, "changes-requested", "exclude hospice patients", "rev-bob");

        MeasureDefinitionEntity back = measures.findById(measureId).orElseThrow();
        assertThat(back.getStatus()).isEqualTo("draft");
        assertThat(back.getReviewComment()).isEqualTo("exclude hospice patients");
        ApprovalReadiness readiness = definitions.getApprovalReadiness(measureId, "rev-bob");
        assertThat(readiness.isReady()).as("in draft the request is a warning, so the author can resubmit").isTrue();
        assertThat(readiness.getWarnings()).extracting(ApprovalReadiness.Item::getCode).contains(ApprovalReadiness.REVIEW_CHANGES_REQUESTED);
        assertThat(readiness.getReview().getChangesRequested()).isEqualTo(1);

        definitions.submitForReview(measureId, "rev-alice");

        List<MeasureReviewerEntity> rows = reviewers.findByMeasureIdAndTenantIdOrderByAssignedAtAsc(measureId, TENANT);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDecision()).as("a new round").isEqualTo("pending");
        assertThat(rows.get(0).getDecidedAt()).isNull();
        ApprovalReadiness again = definitions.getApprovalReadiness(measureId, "rev-bob");
        assertThat(again.getWarnings()).extracting(ApprovalReadiness.Item::getCode).contains(ApprovalReadiness.REVIEWERS_PENDING);
        assertThat(again.getBlockers()).isEmpty();
    }

    @Test
    void commentThreads_areOneLevelDeep_resolvableFromAnyReply_andReadableByParticipantsOnly() {
        review.setReviewers(measureId, List.of("rev-bob"), "rev-alice");
        MeasureReview.Comment root = review.addComment(measureId, new MeasureReview.CommentRequest("Age 18 or 20?", null, "populations", "g1:initial-population"), "rev-alice");
        MeasureReview.Comment reply = review.addComment(measureId, new MeasureReview.CommentRequest("18 — adult per guideline", root.getId(), null, null), "rev-bob");
        MeasureReview.Comment nested = review.addComment(measureId, new MeasureReview.CommentRequest("Agreed", reply.getId(), null, null), "rev-alice");
        assertThat(nested.getParentId()).isEqualTo(root.getId());

        review.setResolved(measureId, reply.getId(), true, "rev-bob");
        MeasureReview seen = review.getReview(measureId, "rev-bob");
        assertThat(seen.getComments()).hasSize(1);
        assertThat(seen.getComments().get(0).isResolved()).isTrue();
        assertThat(seen.getComments().get(0).getResolvedBy()).isEqualTo("rev-bob");
        assertThat(seen.getComments().get(0).getReplies()).hasSize(2);
        assertThat(seen.getSummary().getResolvedThreads()).isEqualTo(1);
        assertThat(seen.isReviewer()).isTrue();
        assertThat(seen.isCanAssign()).isFalse();

        assertThat(review.isParticipant(measureId, "rev-bob")).isTrue();
        assertThat(review.isParticipant(measureId, "rev-carol")).isFalse();
        // assigning a reviewer made the measure "shared", which every authenticated user may read (same rule as the
        // measure itself) — but a non-participant cannot take part
        MeasureReview outsider = review.getReview(measureId, "rev-carol");
        assertThat(outsider.isCanComment()).isFalse();
        assertThat(outsider.isCanAssign()).isFalse();
        assertThatThrownBy(() -> review.addComment(measureId, new MeasureReview.CommentRequest("hi", null, null, null), "rev-carol"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> review.editComment(measureId, root.getId(), "x", "rev-bob")).isInstanceOf(AccessDeniedException.class);
    }
}
