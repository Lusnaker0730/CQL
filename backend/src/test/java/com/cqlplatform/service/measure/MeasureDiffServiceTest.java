package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureVersionDiff;
import com.cqlplatform.model.measure.MeasureVersionDiff.CqlLine;
import com.cqlplatform.model.measure.MeasureVersionDiff.FieldChange;
import com.cqlplatform.model.measure.MeasureVersionDiff.GroupChange;
import com.cqlplatform.model.measure.MeasureVersionDiff.ItemChange;
import com.cqlplatform.model.measure.ObservationDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import com.cqlplatform.model.measure.StratifierDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PAT-263: the structured version diff — fields by section, groups by id, CQL by line. */
class MeasureDiffServiceTest {

    private final MeasureDiffService service = new MeasureDiffService();

    private static PopulationDefinition pop(String type, String expr) {
        return PopulationDefinition.builder().populationType(type).criteriaExpression(expr).build();
    }

    private static MeasureDefinition.MeasureDefinitionBuilder base() {
        return MeasureDefinition.builder().id(1L).name("HbA1c").version("1.0.0").status("active").title("HbA1c control")
                .scoringType("proportion").improvementNotation("increase").steward("Quality office")
                .measureTypes(List.of("process"))
                .valueSetVersions(Map.of("https://x/ValueSet/dm", "1.0.0"))
                .cqlContent("library HbA1c version '1.0.0'\n\ndefine IP: true\ndefine Denominator: IP\ndefine Numerator: false\n")
                .groupDefinitions(List.of(GroupDefinition.builder().groupId("g1").description("adults")
                        .populations(List.of(pop("initial-population", "IP"), pop("denominator", "Denominator"), pop("numerator", "Numerator")))
                        .stratifiers(List.of(StratifierDefinition.builder().stratifierId("sex").criteriaExpression("Sex").build()))
                        .build()));
    }

    @Test
    void identicalVersions_haveNoChanges_andNoLineDiff() {
        MeasureVersionDiff d = service.diff(base().build(), base().id(2L).version("1.0.1").build());

        assertThat(d.getSummary().getTotal()).isZero();
        assertThat(d.isCqlChanged()).isFalse();
        assertThat(d.getCqlLines()).isEmpty();
        assertThat(d.getMetadata()).isEmpty();
        assertThat(d.getGroups()).extracting(GroupChange::getKind).containsExactly(MeasureVersionDiff.UNCHANGED);
        assertThat(d.getMetadataChanges()).isEmpty();
        assertThat(d.getPopulationChanges()).isEmpty();
        // the version itself is not a "change": that is what is being compared
        assertThat(d.getOldVersion()).isEqualTo("1.0.0");
        assertThat(d.getNewVersion()).isEqualTo("1.0.1");
    }

    @Test
    void metadata_isComparedFieldByField_inSections_withAddedRemovedChanged() {
        MeasureDefinition newer = base().id(2L).version("2.0.0")
                .title("HbA1c control (adults)")          // changed
                .steward(null)                             // removed
                .rationale("Glycaemic control matters")    // added
                .measureTypes(List.of("process", "outcome"))
                .effectiveStart(LocalDate.of(2027, 1, 1))
                .valueSetVersions(Map.of("https://x/ValueSet/dm", "2.0.0"))
                .build();

        MeasureVersionDiff d = service.diff(base().build(), newer);

        assertThat(d.getMetadata()).extracting(FieldChange::getField)
                .containsExactly("title", "rationale", "measureTypes", "steward", "effectiveStart", "valueSetVersions");
        assertThat(d.getMetadata()).extracting(FieldChange::getSection)
                .containsExactly("identity", "description", "scoring", "stewardship", "dates", "terminology");
        assertThat(d.getMetadata().get(0).getKind()).isEqualTo(MeasureVersionDiff.CHANGED);
        assertThat(d.getMetadata().get(1).getKind()).isEqualTo(MeasureVersionDiff.ADDED);
        assertThat(d.getMetadata().get(3).getKind()).isEqualTo(MeasureVersionDiff.REMOVED);
        assertThat(d.getMetadata().get(5).getOldValue()).isEqualTo("https://x/ValueSet/dm = 1.0.0");
        assertThat(d.getMetadata().get(5).getNewValue()).isEqualTo("https://x/ValueSet/dm = 2.0.0");
        // the pre-PAT-263 sentences are still produced
        assertThat(d.getMetadataChanges()).contains(
                "Title changed: \"HbA1c control\" → \"HbA1c control (adults)\"",
                "Steward removed (was: \"Quality office\")",
                "Rationale added: \"Glycaemic control matters\"");
        assertThat(d.getSummary().getMetadata()).isEqualTo(6);
    }

    @Test
    void groups_areMatchedById_populationsByType_stratifiersById_observationsByPosition() {
        MeasureDefinition newer = base().id(2L).version("2.0.0").groupDefinitions(List.of(
                GroupDefinition.builder().groupId("g1").description("adults 18+")
                        .populations(List.of(pop("initial-population", "IP"), pop("denominator", "Denominator"),
                                pop("numerator", "Numerator Controlled"), pop("denominator-exclusion", "Hospice")))
                        .stratifiers(List.of(StratifierDefinition.builder().stratifierId("sex").criteriaExpression("Sex").build(),
                                StratifierDefinition.builder().stratifierId("age").criteriaExpression("Age Band").build()))
                        .observations(List.of(ObservationDefinition.builder().criteriaExpression("Measure Observation").aggregateMethod("average").build()))
                        .build(),
                GroupDefinition.builder().groupId("g2").populations(List.of(pop("initial-population", "IP"))).build())).build();

        MeasureVersionDiff d = service.diff(base().build(), newer);

        assertThat(d.getGroups()).extracting(GroupChange::getGroupId).containsExactly("g1", "g2");
        GroupChange g1 = d.getGroups().get(0);
        assertThat(g1.getKind()).isEqualTo(MeasureVersionDiff.CHANGED);
        assertThat(g1.getFields()).extracting(FieldChange::getField).containsExactly("description");
        assertThat(g1.getPopulations()).extracting(ItemChange::getKey, ItemChange::getKind, ItemChange::getOldValue, ItemChange::getNewValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("numerator", MeasureVersionDiff.CHANGED, "Numerator", "Numerator Controlled"),
                        org.assertj.core.groups.Tuple.tuple("denominator-exclusion", MeasureVersionDiff.ADDED, null, "Hospice"));
        assertThat(g1.getStratifiers()).extracting(ItemChange::getKey, ItemChange::getKind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("age", MeasureVersionDiff.ADDED));
        assertThat(g1.getObservations()).extracting(ItemChange::getKey, ItemChange::getKind, ItemChange::getNewValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("observation #1", MeasureVersionDiff.ADDED, "Measure Observation [average]"));
        GroupChange g2 = d.getGroups().get(1);
        assertThat(g2.getKind()).isEqualTo(MeasureVersionDiff.ADDED);
        assertThat(g2.getPopulations()).extracting(ItemChange::getKind).containsExactly(MeasureVersionDiff.ADDED);

        assertThat(d.getSummary().getGroups()).isEqualTo(1);
        assertThat(d.getSummary().getPopulations()).isEqualTo(3);
        assertThat(d.getSummary().getStratifiers()).isEqualTo(1);
        assertThat(d.getSummary().getObservations()).isEqualTo(1);
        assertThat(d.getPopulationChanges()).contains(
                "g1: population numerator changed: \"Numerator\" → \"Numerator Controlled\"",
                "g1: population denominator-exclusion added: \"Hospice\"",
                "Group g2 added");
    }

    @Test
    void groupsWithoutIds_fallBackToPosition_andARemovedGroupIsReported() {
        MeasureDefinition older = base().groupDefinitions(List.of(
                GroupDefinition.builder().populations(List.of(pop("initial-population", "IP"))).build(),
                GroupDefinition.builder().populations(List.of(pop("initial-population", "IP2"))).build())).build();
        MeasureDefinition newer = base().id(2L).groupDefinitions(List.of(
                GroupDefinition.builder().populations(List.of(pop("initial-population", "IP"))).build())).build();

        MeasureVersionDiff d = service.diff(older, newer);

        assertThat(d.getGroups()).extracting(GroupChange::getGroupId, GroupChange::getKind).containsExactly(
                org.assertj.core.groups.Tuple.tuple("group #1", MeasureVersionDiff.UNCHANGED),
                org.assertj.core.groups.Tuple.tuple("group #2", MeasureVersionDiff.REMOVED));
        assertThat(d.getPopulationChanges()).containsExactly("Group group #2 removed");
    }

    @Test
    void cql_isDiffedLineByLine_withLineNumbersOnBothSides() {
        MeasureDefinition newer = base().id(2L)
                .cqlContent("library HbA1c version '2.0.0'\r\n\r\ndefine IP: true\r\ndefine Denominator: IP and AgeInYears() >= 18\r\ndefine Numerator: false\r\ndefine Hospice: false\r\n")
                .build();

        MeasureVersionDiff d = service.diff(base().build(), newer);

        assertThat(d.isCqlChanged()).isTrue();
        List<CqlLine> lines = d.getCqlLines();
        assertThat(lines).extracting(CqlLine::getType).containsExactly(
                "removed", "added", "equal", "equal", "removed", "added", "equal", "added", "equal");
        assertThat(lines.get(0).getText()).isEqualTo("library HbA1c version '1.0.0'");
        assertThat(lines.get(0).getOldLine()).isEqualTo(1);
        assertThat(lines.get(0).getNewLine()).isNull();
        assertThat(lines.get(1).getText()).isEqualTo("library HbA1c version '2.0.0'");
        assertThat(lines.get(1).getNewLine()).isEqualTo(1);
        assertThat(lines.get(5).getText()).isEqualTo("define Denominator: IP and AgeInYears() >= 18");
        assertThat(lines.get(7).getText()).isEqualTo("define Hospice: false");
        assertThat(d.getSummary().getCqlAdded()).isEqualTo(3);
        assertThat(d.getSummary().getCqlRemoved()).isEqualTo(2);
        assertThat(d.getSummary().getTotal()).isEqualTo(1);
    }

    @Test
    void hugeCql_isReportedAsChanged_withoutLineDetail() {
        String big = "define X: 1\n".repeat(MeasureDiffService.MAX_DIFF_LINES + 5);
        MeasureVersionDiff d = service.diff(base().cqlContent(big).build(), base().id(2L).cqlContent(big + "define Y: 2\n").build());

        assertThat(d.isCqlChanged()).isTrue();
        assertThat(d.getCqlLines()).isNull();
        assertThat(d.getSummary().getCqlAdded()).isZero();
        assertThat(d.getSummary().getTotal()).isEqualTo(1);
    }
}
