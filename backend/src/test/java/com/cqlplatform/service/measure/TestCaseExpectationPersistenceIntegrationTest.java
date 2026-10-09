package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.entity.TestCaseEntity;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.repository.TestCaseRepository;
import com.cqlplatform.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-148 — a change to nothing but the test case's expectation map must reach the database.
 * The map is {@code @Transient} and used to be serialized only in {@code @PreUpdate}, which
 * Hibernate fires only for entities whose persistent fields changed: a PUT that edited only the
 * expectation answered 200 and persisted nothing. Real Hibernate (H2), flush + clear, reload.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TestCaseExpectationPersistenceIntegrationTest {

    private static final Long TENANT = 1L;

    @Autowired private MeasureDefinitionRepository measureRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Long newTestCase() {
        MeasureDefinitionEntity measure = measureRepository.saveAndFlush(MeasureDefinitionEntity.builder()
                .name("Bug148Measure").version("1.0.0").status("draft").scoringType("cohort")
                .ownerUsername("bug148-owner").tenantId(TENANT)
                .cqlContent("library Bug148Measure version '1.0.0'\ndefine \"Initial Population\": true")
                .build());
        TestCaseEntity tc = TestCaseEntity.builder()
                .measureDefinitionId(measure.getId()).title("only the expectation changes")
                .patientBundleJson("{\"resourceType\":\"Bundle\",\"entry\":[]}")
                .expectedPopulationMap(new LinkedHashMap<>(Map.of("Initial Population", false)))
                .build();
        Long id = testCaseRepository.saveAndFlush(tc).getId();
        entityManager.clear();
        return id;
    }

    @Test
    void changingOnlyTheExpectationMap_isPersisted() {
        Long id = newTestCase();

        TestCaseEntity loaded = testCaseRepository.findById(id).orElseThrow();
        assertThat(loaded.getExpectedPopulationMap()).containsEntry("Initial Population", false);
        loaded.setExpectedPopulationMap(new LinkedHashMap<>(Map.of("Initial Population", true)));
        testCaseRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        TestCaseEntity reloaded = testCaseRepository.findById(id).orElseThrow();
        assertThat(reloaded.getExpectedPopulationMap()).as("the map after a reload").containsEntry("Initial Population", true);
        assertThat(reloaded.getExpectedPopulations()).as("the column after a reload").contains("true");
    }

    @Test
    void changingOnlyTheLastRunMap_isPersisted() {
        Long id = newTestCase();

        TestCaseEntity loaded = testCaseRepository.findById(id).orElseThrow();
        assertThat(loaded.getLastRunActualPopulationMap()).isEmpty();
        loaded.setLastRunActualPopulationMap(new LinkedHashMap<>(Map.of("Initial Population", true)));
        testCaseRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        TestCaseEntity reloaded = testCaseRepository.findById(id).orElseThrow();
        assertThat(reloaded.getLastRunActualPopulationMap()).containsEntry("Initial Population", true);
        assertThat(reloaded.getLastRunActualPopulations()).contains("true");
    }

    @Test
    void settingANullMap_storesAnEmptyMap_likeTheLifecycleCallbacksDo() {
        Long id = newTestCase();

        TestCaseEntity loaded = testCaseRepository.findById(id).orElseThrow();
        loaded.setExpectedPopulationMap(null);
        testCaseRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        TestCaseEntity reloaded = testCaseRepository.findById(id).orElseThrow();
        assertThat(reloaded.getExpectedPopulationMap()).isEmpty();
        assertThat(reloaded.getExpectedPopulations()).isEqualTo("{}");
    }
}
