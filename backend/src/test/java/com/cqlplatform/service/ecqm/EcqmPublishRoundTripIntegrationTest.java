package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.EcqmArtifactEntity;
import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.exception.PublishConflictException;
import com.cqlplatform.model.ecqm.BuilderSource;
import com.cqlplatform.repository.EcqmArtifactRepository;
import com.cqlplatform.repository.MeasureDefinitionRepository;
import com.cqlplatform.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-238 — the publish fingerprint through the real persistence path: publish writes the
 * measure's CQL and group definitions to their JSON columns; after a flush + clear the measure
 * is re-read from the database and must still match (no spurious "edited since publish"), and a
 * real edit to the stored measure must be detected before the next publish writes anything.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EcqmPublishRoundTripIntegrationTest {

    private static final Long TENANT = 1L;
    private static final String OWNER = "roundtrip-owner";

    @Autowired private EcqmPublishService publishService;
    @Autowired private EcqmArtifactRepository artifactRepository;
    @Autowired private MeasureDefinitionRepository measureRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static Map<String, Object> ageTree(String name, String min) {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("id", "AgeRange");
        child.put("name", "Age Range");
        child.put("type", "AgeRange");
        child.put("returnType", "boolean");
        child.put("fields", List.of(
                Map.of("id", "element_name", "type", "string", "value", name),
                Map.of("id", "min_age", "type", "string", "value", min),
                Map.of("id", "max_age", "type", "string", "value", ""),
                Map.of("id", "unit_of_time", "type", "string", "value", "year")));
        child.put("modifiers", new ArrayList<>());
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>(List.of(child)));
        return tree;
    }

    private Long newArtifact() {
        Map<String, Object> pops = new LinkedHashMap<>();
        pops.put("initial-population", ageTree("Adult", "18"));
        pops.put("denominator", ageTree("Fifty", "50"));
        pops.put("numerator", ageTree("Senior", "65"));
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupId", "group-1");
        group.put("populations", pops);
        EcqmArtifactEntity artifact = EcqmArtifactEntity.builder()
                .name("RoundTripMeasure").version("1.0.0").status("draft").fhirVersion("4.0.1")
                .scoringType("proportion").populationBasis("boolean").improvementNotation("increase")
                .ownerUsername(OWNER).tenantId(TENANT)
                .populationGroupsList(new ArrayList<>(List.of(group)))
                .stratifiersList(new ArrayList<>(List.of(Map.of("stratifierId", "sex", "kind", "value", "value", Map.of("source", "gender")))))
                .build();
        return artifactRepository.saveAndFlush(artifact).getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void republishAfterADatabaseRoundTrip_isClean_andARealEditIsDetected() {
        Long artifactId = newArtifact();
        Long measureId = publishService.publish(artifactId, OWNER).getMeasureDefinitionId();
        flushAndClear();

        // re-read from the database: nothing changed, so no drift and re-publish goes through
        BuilderSource clean = publishService.builderSourceOf(measureId).orElseThrow();
        assertThat(clean.getArtifactId()).isEqualTo(artifactId);
        assertThat(clean.getMeasureEditedSincePublish()).isFalse();
        publishService.publish(artifactId, OWNER);
        flushAndClear();

        // an edit on the measure page (stored measure changes) is caught before anything is written
        MeasureDefinitionEntity measure = measureRepository.findByIdAndTenantId(measureId, TENANT).orElseThrow();
        measure.setCqlContent(measure.getCqlContent() + "\ndefine \"Hand edit\": true\n");
        measureRepository.saveAndFlush(measure);
        entityManager.clear();

        assertThat(publishService.builderSourceOf(measureId).orElseThrow().getMeasureEditedSincePublish()).isTrue();
        assertThatThrownBy(() -> publishService.publish(artifactId, OWNER)).isInstanceOf(PublishConflictException.class);
        entityManager.clear();
        assertThat(measureRepository.findByIdAndTenantId(measureId, TENANT).orElseThrow().getCqlContent()).contains("Hand edit");

        // forced: overwritten and re-baselined
        publishService.publish(artifactId, OWNER, true);
        flushAndClear();
        assertThat(measureRepository.findByIdAndTenantId(measureId, TENANT).orElseThrow().getCqlContent()).doesNotContain("Hand edit");
        assertThat(publishService.builderSourceOf(measureId).orElseThrow().getMeasureEditedSincePublish()).isFalse();
    }
}
