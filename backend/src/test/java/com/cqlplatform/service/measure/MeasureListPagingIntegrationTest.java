package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureListQuery;
import com.cqlplatform.model.measure.MeasureListQuery.Scope;
import com.cqlplatform.model.measure.MeasurePage;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-257 — the paged measure list against a real (H2) database: tenant scoping, status / department /
 * scope filters, LIKE escaping, stable sort + paging totals, and the validation of sort field and status.
 * Names carry a unique prefix so rows other tests may have left behind do not leak into the totals.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MeasureListPagingIntegrationTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER_TENANT = 2L;
    private static final String P = "plist-";

    @Autowired private MeasureDefinitionRepository repository;
    @Autowired private MeasureDefinitionService service;

    @BeforeEach
    void seed() {
        TenantContext.setCurrentTenantId(TENANT);
        save(TENANT, P + "Alpha_1", "draft", "alice", List.of(), "private", "cardio");
        save(TENANT, P + "Beta", "active", "bob", List.of("alice"), "shared", "cardio");
        save(TENANT, P + "Gamma", "active", "bob", List.of(), "public", "neuro");
        save(TENANT, P + "Delta", "retired", "alice", List.of(), "private", null);
        save(OTHER_TENANT, P + "Alpha-elsewhere", "draft", "alice", List.of(), "private", "cardio");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void save(Long tenant, String name, String status, String owner, List<String> sharedWith, String access, String department) {
        repository.saveAndFlush(MeasureDefinitionEntity.builder()
                .name(name).version("1.0.0").status(status).scoringType("proportion")
                // the JSON column is written from the transient list in @PrePersist, so set the list, not the string
                .ownerUsername(owner).sharedWithList(new java.util.ArrayList<>(sharedWith)).accessLevel(access).department(department)
                .tenantId(tenant).build());
    }

    private static MeasureListQuery q(String search, String department, List<String> statuses, Scope scope, String user) {
        return new MeasureListQuery(search, department, statuses, scope, user);
    }

    private static List<String> names(MeasurePage page) {
        return page.getContent().stream().map(MeasureDefinition::getName).toList();
    }

    @Test
    void tenantScoped_sortedByName_andPagedWithTotals() {
        MeasurePage first = service.listPage(q(P, null, List.of(), Scope.ALL, "alice"), 0, 2, "name", false);
        assertThat(first.getTotalElements()).isEqualTo(4);          // the other tenant's Alpha is not counted
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(names(first)).containsExactly(P + "Alpha_1", P + "Beta");

        MeasurePage second = service.listPage(q(P, null, List.of(), Scope.ALL, "alice"), 1, 2, "name", false);
        assertThat(names(second)).containsExactly(P + "Delta", P + "Gamma");

        MeasurePage desc = service.listPage(q(P, null, List.of(), Scope.ALL, "alice"), 0, 10, "name", true);
        assertThat(names(desc)).containsExactly(P + "Gamma", P + "Delta", P + "Beta", P + "Alpha_1");
    }

    @Test
    void statusDepartmentAndScopeFilters() {
        assertThat(names(service.listPage(q(P, null, List.of("active"), Scope.ALL, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Beta", P + "Gamma");
        assertThat(names(service.listPage(q(P, null, List.of("draft", "retired"), Scope.ALL, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Alpha_1", P + "Delta");
        assertThat(names(service.listPage(q(P, "cardio", List.of(), Scope.ALL, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Alpha_1", P + "Beta");
        assertThat(names(service.listPage(q(P, null, List.of(), Scope.MINE, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Alpha_1", P + "Delta");
        assertThat(names(service.listPage(q(P, null, List.of(), Scope.SHARED, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Beta");                          // shared with alice, not hers; public Gamma is not "shared"
        assertThat(names(service.listPage(q(P, null, List.of(), Scope.PUBLIC, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Gamma");
    }

    @Test
    void searchIsCaseInsensitive_andLikeWildcardsAreLiteral() {
        assertThat(names(service.listPage(q(P + "alpha", null, List.of(), Scope.ALL, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Alpha_1");
        // `_` must not act as a single-character wildcard: "Alpha_" matches Alpha_1, not Alpha-elsewhere (other tenant anyway) or Alpha1
        assertThat(names(service.listPage(q(P + "Alpha_", null, List.of(), Scope.ALL, "alice"), 0, 10, "name", false)))
                .containsExactly(P + "Alpha_1");
        assertThat(names(service.listPage(q(P + "Alpha%", null, List.of(), Scope.ALL, "alice"), 0, 10, "name", false))).isEmpty();
    }

    @Test
    void unknownSortFieldOrStatus_isA400() {
        assertThatThrownBy(() -> service.listPage(q(P, null, List.of(), Scope.ALL, "alice"), 0, 10, "cqlContent", false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("sort");
        assertThatThrownBy(() -> service.listPage(q(P, null, List.of("published"), Scope.ALL, "alice"), 0, 10, "name", false))
                .isInstanceOf(ValidationException.class).hasMessageContaining("status");
    }
}
