package com.cqlplatform.service.ecqm;

import com.cqlplatform.entity.MeasureDefinitionEntity;
import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-238 — the publish fingerprint must survive a database round trip and cosmetic differences
 * (line endings, trailing whitespace) but change when the logic or the population mapping does.
 */
class PublishedContentTest {

    private static List<GroupDefinition> groups(String numerator) {
        return new ArrayList<>(List.of(GroupDefinition.builder()
                .groupId("group-1").populationBasis("boolean")
                .populations(List.of(
                        PopulationDefinition.builder().populationType("initial-population").criteriaExpression("Initial Population").build(),
                        PopulationDefinition.builder().populationType("denominator").criteriaExpression("Denominator").build(),
                        PopulationDefinition.builder().populationType("numerator").criteriaExpression(numerator).build()))
                .stratifiers(List.of(StratifierDefinition.builder().stratifierId("sex").criteriaExpression("Stratifier sex").kind("value").build()))
                .build()));
    }

    private static final String CQL = "library M version '1.0.0'\nusing FHIR version '4.0.1'\ndefine \"Numerator\": true\n";

    @Test
    void sameContent_sameHash_despiteLineEndingsAndTrailingWhitespace() {
        assertThat(PublishedContent.hash(CQL, groups("Numerator")))
                .isEqualTo(PublishedContent.hash(CQL.replace("\n", "\r\n") + "  \n\n", groups("Numerator")));
    }

    @Test
    void survivesTheMeasureEntityDatabaseRoundTrip() {
        List<GroupDefinition> published = groups("Numerator");
        MeasureDefinitionEntity entity = MeasureDefinitionEntity.builder().cqlContent(CQL).groupDefinitionList(published).build();
        ReflectionTestUtils.invokeMethod(entity, "onCreate");       // serialise to the JSON columns
        entity.setGroupDefinitionList(new ArrayList<>());            // what a fresh load starts from
        ReflectionTestUtils.invokeMethod(entity, "onLoad");         // deserialise like @PostLoad

        assertThat(PublishedContent.hash(entity.getCqlContent(), entity.getGroupDefinitionList()))
                .isEqualTo(PublishedContent.hash(CQL, published));
    }

    @Test
    void differentLogicOrMapping_differentHash() {
        String base = PublishedContent.hash(CQL, groups("Numerator"));
        assertThat(PublishedContent.hash(CQL.replace("true", "false"), groups("Numerator"))).isNotEqualTo(base);
        assertThat(PublishedContent.hash(CQL, groups("Numerator Alt"))).isNotEqualTo(base);
        assertThat(PublishedContent.hash(CQL, List.of())).isNotEqualTo(base);
        assertThat(PublishedContent.hash(null, null)).isEqualTo(PublishedContent.hash("", List.of()));
    }
}
