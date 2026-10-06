package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.MeasureSetEntity;
import com.cqlplatform.exception.ResourceLockedException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.MeasureSetRepository;
import com.cqlplatform.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-253 through real persistence: a measure created through the service opens a measure set;
 * a new version joins it; renaming one version keeps the lineage (history, version uniqueness)
 * together; sharing on one version reaches the other; approving the renamed new version retires
 * the old active one; and a test case locked by one user refuses the other user's write until it
 * is released.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeasureSetLineageIntegrationTest {

    private static final Long TENANT = 1L;
    private static final String OWNER = "lineage-owner";
    private static final String REVIEWER = "lineage-reviewer";
    private static final String CQL = "library LineageMeasure version '1.0.0'\n\nusing FHIR version '4.0.1'\n\n"
            + "context Patient\n\ndefine \"Initial Population\":\n  true\n";

    @Autowired private MeasureDefinitionService measureDefinitionService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private MeasureDefinitionRepository measureRepository;
    @Autowired private MeasureSetRepository measureSetRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void authenticateOwner() {
        TenantContext.setCurrentTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OWNER, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private MeasureDefinition dto(Long id) {
        return measureDefinitionService.getById(id).orElseThrow();
    }

    @Test
    void lineageSurvivesARename_sharingSpansTheSet_andApprovalSupersedesWithinTheSet() {
        MeasureDefinition v1 = measureDefinitionService.create(MeasureDefinition.builder()
                .name("LineageMeasure").version("1.0.0").scoringType("cohort").cqlContent(CQL).build());
        flushAndClear();
        assertThat(v1.getMeasureSetId()).isNotNull();
        MeasureSetEntity set = measureSetRepository.findByIdAndTenantId(v1.getMeasureSetId(), TENANT).orElseThrow();
        assertThat(set.getName()).isEqualTo("LineageMeasure");

        // v1 is approved by a reviewer it was shared with (four-eyes)
        measureDefinitionService.submitForReview(v1.getId(), OWNER);
        measureDefinitionService.shareMeasure(v1.getId(), REVIEWER, OWNER);
        measureDefinitionService.approveMeasure(v1.getId(), REVIEWER);
        flushAndClear();
        assertThat(dto(v1.getId()).getStatus()).isEqualTo("active");

        // a new version joins the set; a second 1.0.0 in the set is refused
        MeasureDefinition v2 = measureDefinitionService.createVersionAs(v1.getId(), "2.0.0");
        flushAndClear();
        assertThat(v2.getMeasureSetId()).isEqualTo(v1.getMeasureSetId());
        assertThatThrownBy(() -> measureDefinitionService.createVersionAs(v1.getId(), "2.0.0"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Version already exists");

        // renaming the draft keeps it in the lineage and renames the set
        MeasureDefinition rename = dto(v2.getId());
        rename.setName("LineageMeasureRenamed");
        measureDefinitionService.update(v2.getId(), rename, OWNER);
        flushAndClear();
        assertThat(measureSetRepository.findByIdAndTenantId(v1.getMeasureSetId(), TENANT).orElseThrow().getName())
                .isEqualTo("LineageMeasureRenamed");
        List<MeasureDefinition> history = measureDefinitionService.getHistory(dto(v1.getId()));
        assertThat(history).extracting(MeasureDefinition::getId).containsExactly(v2.getId(), v1.getId());
        assertThat(history).extracting(MeasureDefinition::getName).containsExactly("LineageMeasureRenamed", "LineageMeasure");

        // the reviewer shared on v1 is on v2 too (copied at version time, and sharing applies set-wide)
        measureDefinitionService.unshareMeasure(v1.getId(), REVIEWER, OWNER);
        flushAndClear();
        assertThat(dto(v2.getId()).getSharedWith()).doesNotContain(REVIEWER);
        measureDefinitionService.shareMeasure(v2.getId(), REVIEWER, OWNER);
        flushAndClear();
        assertThat(dto(v1.getId()).getSharedWith()).contains(REVIEWER);

        // approving the renamed v2 retires v1 — the set, not the name, is the lineage
        measureDefinitionService.submitForReview(v2.getId(), OWNER);
        measureDefinitionService.approveMeasure(v2.getId(), REVIEWER);
        flushAndClear();
        assertThat(dto(v2.getId()).getStatus()).isEqualTo("active");
        assertThat(dto(v1.getId()).getStatus()).isEqualTo("retired");

        // a measure that merely shares the old name is its own lineage
        MeasureDefinition stranger = measureDefinitionService.create(MeasureDefinition.builder()
                .name("LineageMeasure").version("3.0.0").scoringType("cohort").cqlContent(CQL).build());
        flushAndClear();
        assertThat(stranger.getMeasureSetId()).isNotEqualTo(v1.getMeasureSetId());
        assertThat(measureDefinitionService.getHistory(dto(stranger.getId()))).extracting(MeasureDefinition::getId)
                .containsExactly(stranger.getId());
    }

    @Test
    void aTestCaseLockedByOneUser_refusesTheOtherUsersWrite_untilReleased() {
        MeasureDefinition measure = measureDefinitionService.create(MeasureDefinition.builder()
                .name("LockMeasure").version("1.0.0").scoringType("cohort").cqlContent(CQL).build());
        TestCase created = testCaseService.create(measure.getId(), TestCase.builder().title("locked case").build());
        flushAndClear();

        TestCase locked = testCaseService.lock(created.getId(), OWNER);
        assertThat(locked.getLockedBy()).isEqualTo(OWNER);
        assertThat(locked.getLockExpiresAt()).isAfter(locked.getLockedAt());
        flushAndClear();

        TestCase edit = TestCase.builder().title("edited by someone else").build();
        assertThatThrownBy(() -> testCaseService.update(created.getId(), edit, REVIEWER))
                .isInstanceOf(ResourceLockedException.class)
                .satisfies(ex -> assertThat(((ResourceLockedException) ex).getDetails()).anyMatch(d -> d.startsWith("lockedBy: " + OWNER)));
        assertThatThrownBy(() -> testCaseService.delete(created.getId(), REVIEWER)).isInstanceOf(ResourceLockedException.class);
        flushAndClear();
        assertThat(testCaseService.getById(created.getId()).orElseThrow().getTitle()).isEqualTo("locked case");

        // the holder's own write goes through and keeps the lock; releasing it lets the other user in
        assertThat(testCaseService.update(created.getId(), TestCase.builder().title("edited by holder").build(), OWNER).getLockedBy()).isEqualTo(OWNER);
        testCaseService.unlock(created.getId(), OWNER);
        flushAndClear();
        assertThat(testCaseService.update(created.getId(), edit, REVIEWER).getTitle()).isEqualTo("edited by someone else");
    }

    @Test
    void rowsWithoutASet_stillHaveANameBasedHistory() {
        MeasureDefinitionEntity legacy = measureRepository.saveAndFlush(MeasureDefinitionEntity.builder()
                .name("PreV79").version("1.0.0").status("draft").scoringType("cohort").ownerUsername(OWNER).tenantId(TENANT).build());
        entityManager.clear();

        assertThat(measureDefinitionService.getHistory(dto(legacy.getId()))).extracting(MeasureDefinition::getId)
                .containsExactly(legacy.getId());
    }
}
