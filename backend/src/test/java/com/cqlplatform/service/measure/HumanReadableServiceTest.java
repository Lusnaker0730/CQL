package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-236 — the human-readable narrative shows the standard metadata (type, dates, experimental,
 * clinical recommendation statement, definitions), only when present, escaped, with unique ids.
 */
class HumanReadableServiceTest {

    private final HumanReadableService service = new HumanReadableService();

    private static MeasureDefinition.MeasureDefinitionBuilder base() {
        return MeasureDefinition.builder().name("Demo").version("1.0.0").title("Demo measure")
                .status("draft").scoringType("proportion").cqlContent("library Demo version '1.0.0'");
    }

    @Test
    void standardMetadata_isRendered_withTocEntries_andEscaping() {
        String html = service.generateHtml(base()
                .measureTypes(List.of("process", "outcome"))
                .definitionTerms(List.of(MeasureDefinition.DefinitionTerm.builder().term("HbA1c <b>control</b>").definition("< 7%").build()))
                .clinicalRecommendationStatement("ADA & friends recommend <7%")
                .effectiveStart(LocalDate.of(2026, 1, 1)).effectiveEnd(LocalDate.of(2026, 12, 31))
                .approvalDate(LocalDate.of(2025, 11, 20)).lastReviewDate(LocalDate.of(2026, 6, 15))
                .experimental(Boolean.TRUE)
                .build());

        assertThat(html).contains("<section id=\"standard-metadata\">", "<h2>Measure Metadata</h2>");
        assertThat(html).contains("<th>Measure Type</th><td>process, outcome</td>");
        assertThat(html).contains("<th>Effective Period</th><td>2026-01-01 – 2026-12-31</td>");
        assertThat(html).contains("<th>Approval Date</th><td>2025-11-20</td>", "<th>Last Review Date</th><td>2026-06-15</td>");
        assertThat(html).contains("<th>Experimental</th><td>Yes");
        assertThat(html).contains("<section id=\"recommendation\">", "ADA &amp; friends recommend &lt;7%");
        assertThat(html).contains("<section id=\"definitions\">", "<dt>HbA1c &lt;b&gt;control&lt;/b&gt;</dt>", "<dd>&lt; 7%</dd>");
        assertThat(html).contains("href=\"#standard-metadata\"", "href=\"#recommendation\"", "href=\"#definitions\"");
        // the platform's own trailing "Metadata" section keeps its id; ours does not collide with it
        assertThat(html.split("id=\"metadata\"")).hasSize(2);
    }

    @Test
    void standardMetadata_absent_rendersNothingExtra() {
        String html = service.generateHtml(base().experimental(Boolean.FALSE).build());

        assertThat(html).doesNotContain("standard-metadata", "Measure Metadata", "id=\"recommendation\"", "id=\"definitions\"");
        assertThat(html).doesNotContain("href=\"#recommendation\"", "href=\"#definitions\"");
    }

    @Test
    void openEndedEffectivePeriod_showsAnEllipsisForTheMissingEnd() {
        String html = service.generateHtml(base().effectiveStart(LocalDate.of(2026, 1, 1)).build());

        assertThat(html).contains("<th>Effective Period</th><td>2026-01-01 – …</td>");
    }

    // PAT-242
    @Test
    void measurementPeriod_isRenderedInTheMetadataTable() {
        String html = service.generateHtml(base()
                .measurementPeriodStart(LocalDate.of(2024, 1, 1)).measurementPeriodEnd(LocalDate.of(2024, 12, 31)).build());

        assertThat(html).contains("<section id=\"standard-metadata\">", "<th>Measurement Period</th><td>2024-01-01 – 2024-12-31</td>");
    }

    // ------------------------------------------------------------------ PAT-263 version diff report

    @Test
    void diffReport_rendersSummary_metadataTable_groupTables_andTheCqlLineDiff_escaped() {
        com.cqlplatform.model.measure.MeasureVersionDiff diff = new MeasureDiffService().diff(
                base().id(1L).steward("Quality <office>").groupDefinitions(List.of(com.cqlplatform.model.measure.GroupDefinition.builder().groupId("g1")
                        .populations(List.of(com.cqlplatform.model.measure.PopulationDefinition.builder().populationType("numerator").criteriaExpression("Numerator").build())).build())).build(),
                base().id(2L).version("1.1.0").steward("Quality office").cqlContent("library Demo version '1.1.0'\ndefine IP: a < b")
                        .groupDefinitions(List.of(com.cqlplatform.model.measure.GroupDefinition.builder().groupId("g1")
                        .populations(List.of(com.cqlplatform.model.measure.PopulationDefinition.builder().populationType("numerator").criteriaExpression("Numerator & Controlled").build())).build())).build());

        String html = service.generateDiffHtml(diff);

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<title>Demo — v1.0.0 → v1.1.0</title>");
        assertThat(html).contains("<strong>1</strong> metadata field(s)").contains("<strong>1</strong> population(s)");
        assertThat(html).contains("<td>stewardship</td><td>steward</td>").contains("Quality &lt;office&gt;");
        assertThat(html).contains("<td class=\"pop-type\">Numerator</td>").contains("Numerator &amp; Controlled");
        assertThat(html).contains("<span class=\"line removed\">").contains("<span class=\"line added\">");
        assertThat(html).contains("define IP: a &lt; b");
        assertThat(html).doesNotContain("<office>");
    }

    @Test
    void diffReport_ofIdenticalVersions_saysSo() {
        com.cqlplatform.model.measure.MeasureVersionDiff diff = new MeasureDiffService().diff(base().id(1L).build(), base().id(2L).version("1.0.1").build());

        String html = service.generateDiffHtml(diff);

        assertThat(html).contains("The two versions are identical.").contains("No metadata changes.")
                .contains("No population criteria changes.").contains("CQL unchanged.");
    }
}
