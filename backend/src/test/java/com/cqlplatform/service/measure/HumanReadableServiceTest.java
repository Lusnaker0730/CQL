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
}
