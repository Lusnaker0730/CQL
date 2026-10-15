package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a human-readable HTML narrative document from a MeasureDefinition.
 * Suitable for clinical committee review, printing, or embedding in reports.
 */
@Service
@Slf4j
public class HumanReadableService {

    private static final Map<String, String> POPULATION_LABELS = Map.of(
            "initial-population", "Initial Population",
            "denominator", "Denominator",
            "denominator-exclusion", "Denominator Exclusion",
            "denominator-exception", "Denominator Exception",
            "numerator", "Numerator",
            "numerator-exclusion", "Numerator Exclusion",
            "measure-population", "Measure Population",
            "measure-population-exclusion", "Measure Population Exclusion"
    );

    private static final Map<String, String> SCORING_LABELS = Map.of(
            "proportion", "Proportion",
            "ratio", "Ratio",
            "continuous-variable", "Continuous Variable",
            "cohort", "Cohort",
            "composite", "Composite"
    );

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "draft", "Draft",
            "active", "Active",
            "retired", "Retired",
            "in-review", "In Review"
    );

    public String generateHtml(MeasureDefinition measure) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(esc(measure.getTitle() != null ? measure.getTitle() : measure.getName())).append("</title>\n");
        sb.append(CSS);
        sb.append("</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>").append(esc(measure.getTitle() != null ? measure.getTitle() : measure.getName())).append("</h1>\n");
        if (measure.getVersion() != null) {
            sb.append("  <div class=\"version\">Version ").append(esc(measure.getVersion())).append("</div>\n");
        }
        sb.append("  <div class=\"meta-row\">\n");
        sb.append("    <span class=\"badge badge-").append(measure.getStatus() != null ? measure.getStatus() : "draft")
                .append("\">").append(esc(STATUS_LABELS.getOrDefault(measure.getStatus(), "Draft"))).append("</span>\n");
        if (measure.getScoringType() != null) {
            sb.append("    <span class=\"badge badge-scoring\">")
                    .append(esc(SCORING_LABELS.getOrDefault(measure.getScoringType(), measure.getScoringType())))
                    .append("</span>\n");
        }
        if (measure.getCmsMeasureId() != null && !measure.getCmsMeasureId().isBlank()) {
            sb.append("    <span class=\"badge\">CMS ").append(esc(measure.getCmsMeasureId())).append("</span>\n");
        }
        if (measure.getNqfNumber() != null && !measure.getNqfNumber().isBlank()) {
            sb.append("    <span class=\"badge\">NQF #").append(esc(measure.getNqfNumber())).append("</span>\n");
        }
        sb.append("  </div>\n");
        sb.append("</div>\n\n");

        sb.append("<div class=\"container\">\n");

        // Table of Contents
        sb.append("<nav class=\"toc\">\n");
        sb.append("  <h2>Table of Contents</h2>\n<ul>\n");
        sb.append("    <li><a href=\"#description\">Description</a></li>\n");
        // PAT-236 standard metadata sections (each only when it has content)
        if (hasStandardMetadataTable(measure)) sb.append("    <li><a href=\"#standard-metadata\">Measure Metadata</a></li>\n");
        if (notBlank(measure.getClinicalRecommendationStatement())) sb.append("    <li><a href=\"#recommendation\">Clinical Recommendation Statement</a></li>\n");
        if (measure.getDefinitionTerms() != null && !measure.getDefinitionTerms().isEmpty()) sb.append("    <li><a href=\"#definitions\">Definitions</a></li>\n");
        if (measure.getRationale() != null) sb.append("    <li><a href=\"#rationale\">Rationale</a></li>\n");
        if (measure.getClinicalGuidance() != null) sb.append("    <li><a href=\"#guidance\">Clinical Guidance</a></li>\n");
        sb.append("    <li><a href=\"#populations\">Population Criteria</a></li>\n");
        sb.append("    <li><a href=\"#cql\">CQL Logic</a></li>\n");
        if (hasSupplementalData(measure)) sb.append("    <li><a href=\"#supplemental\">Supplemental Data & Risk Adjustment</a></li>\n");
        sb.append("    <li><a href=\"#metadata\">Metadata</a></li>\n");
        sb.append("  </ul>\n</nav>\n\n");

        // Description
        sb.append("<section id=\"description\">\n");
        sb.append("  <h2>Description</h2>\n");
        sb.append("  <p>").append(esc(measure.getDescription() != null ? measure.getDescription() : "No description provided.")).append("</p>\n");
        sb.append("</section>\n\n");

        // PAT-236: standard metadata — type, dates, experimental, clinical recommendation, definitions
        boolean hasTypes = measure.getMeasureTypes() != null && !measure.getMeasureTypes().isEmpty();
        if (hasStandardMetadataTable(measure)) {
            sb.append("<section id=\"standard-metadata\">\n");
            sb.append("  <h2>Measure Metadata</h2>\n");
            sb.append("  <table>\n    <tbody>\n");
            if (hasTypes) sb.append("      <tr><th>Measure Type</th><td>").append(esc(String.join(", ", measure.getMeasureTypes()))).append("</td></tr>\n");
            if (measure.getEffectiveStart() != null || measure.getEffectiveEnd() != null) {
                sb.append("      <tr><th>Effective Period</th><td>")
                        .append(esc(measure.getEffectiveStart() != null ? measure.getEffectiveStart().toString() : "…"))
                        .append(" – ")
                        .append(esc(measure.getEffectiveEnd() != null ? measure.getEffectiveEnd().toString() : "…"))
                        .append("</td></tr>\n");
            }
            if (measure.getMeasurementPeriodStart() != null || measure.getMeasurementPeriodEnd() != null) {
                sb.append("      <tr><th>Measurement Period</th><td>")
                        .append(esc(measure.getMeasurementPeriodStart() != null ? measure.getMeasurementPeriodStart().toString() : "…"))
                        .append(" – ")
                        .append(esc(measure.getMeasurementPeriodEnd() != null ? measure.getMeasurementPeriodEnd().toString() : "…"))
                        .append("</td></tr>\n");
            }
            if (measure.getApprovalDate() != null) sb.append("      <tr><th>Approval Date</th><td>").append(measure.getApprovalDate()).append("</td></tr>\n");
            if (measure.getLastReviewDate() != null) sb.append("      <tr><th>Last Review Date</th><td>").append(measure.getLastReviewDate()).append("</td></tr>\n");
            if (Boolean.TRUE.equals(measure.getExperimental())) sb.append("      <tr><th>Experimental</th><td>Yes — for testing, not for real-world use</td></tr>\n");
            sb.append("    </tbody>\n  </table>\n</section>\n\n");
        }
        if (notBlank(measure.getClinicalRecommendationStatement())) {
            sb.append("<section id=\"recommendation\">\n");
            sb.append("  <h2>Clinical Recommendation Statement</h2>\n");
            sb.append("  <p>").append(esc(measure.getClinicalRecommendationStatement())).append("</p>\n");
            sb.append("</section>\n\n");
        }
        if (measure.getDefinitionTerms() != null && !measure.getDefinitionTerms().isEmpty()) {
            sb.append("<section id=\"definitions\">\n");
            sb.append("  <h2>Definitions</h2>\n  <dl>\n");
            for (MeasureDefinition.DefinitionTerm term : measure.getDefinitionTerms()) {
                sb.append("    <dt>").append(esc(term.getTerm() != null ? term.getTerm() : "")).append("</dt>\n");
                sb.append("    <dd>").append(esc(term.getDefinition() != null ? term.getDefinition() : "")).append("</dd>\n");
            }
            sb.append("  </dl>\n</section>\n\n");
        }

        // Rationale
        if (measure.getRationale() != null && !measure.getRationale().isBlank()) {
            sb.append("<section id=\"rationale\">\n");
            sb.append("  <h2>Rationale</h2>\n");
            sb.append("  <p>").append(esc(measure.getRationale())).append("</p>\n");
            sb.append("</section>\n\n");
        }

        // Clinical Guidance
        if (measure.getClinicalGuidance() != null && !measure.getClinicalGuidance().isBlank()) {
            sb.append("<section id=\"guidance\">\n");
            sb.append("  <h2>Clinical Guidance</h2>\n");
            sb.append("  <p>").append(esc(measure.getClinicalGuidance())).append("</p>\n");
            sb.append("</section>\n\n");
        }

        // Population Criteria
        sb.append("<section id=\"populations\">\n");
        sb.append("  <h2>Population Criteria</h2>\n");
        renderPopulationCriteria(sb, measure);
        sb.append("</section>\n\n");

        // CQL Logic
        sb.append("<section id=\"cql\">\n");
        sb.append("  <h2>CQL Logic</h2>\n");
        if (measure.getCqlContent() != null && !measure.getCqlContent().isBlank()) {
            sb.append("  <pre class=\"cql-code\">").append(esc(measure.getCqlContent())).append("</pre>\n");
        } else {
            sb.append("  <p class=\"empty\">No CQL content defined.</p>\n");
        }
        sb.append("</section>\n\n");

        // Supplemental Data & Risk Adjustment
        if (hasSupplementalData(measure)) {
            sb.append("<section id=\"supplemental\">\n");
            sb.append("  <h2>Supplemental Data & Risk Adjustment</h2>\n");
            renderSupplementalData(sb, measure);
            sb.append("</section>\n\n");
        }

        // Metadata
        sb.append("<section id=\"metadata\">\n");
        sb.append("  <h2>Metadata</h2>\n");
        renderMetadata(sb, measure);
        sb.append("</section>\n\n");

        // Footer
        sb.append("<footer>\n");
        sb.append("  <p>Generated by CQL Platform on ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("</p>\n");
        sb.append("</footer>\n");

        sb.append("</div>\n</body>\n</html>");

        return sb.toString();
    }

    // ------------------------------------------------------------------ PAT-263 version diff report

    private static final String DIFF_CSS = """
            <style>
              .summary-list { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 8px 20px; }
              .summary-list li { font-size: 0.95em; }
              .kind { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 0.75em; font-weight: 600; text-transform: uppercase; }
              .kind-added { background: #e6ffed; color: #1b7f3b; }
              .kind-removed { background: #ffeef0; color: #b31d28; }
              .kind-changed { background: #fff5d6; color: #8a6d00; }
              td.old { background: #fff8f8; color: #6a2a2a; }
              td.new { background: #f6fff8; color: #1f4d2b; }
              pre.cql-diff { background: #f8f9fa; border: 1px solid #e0e0e0; border-radius: 6px; padding: 0; overflow-x: auto; font-size: 0.85em; }
              pre.cql-diff .line { display: block; padding: 0 8px; white-space: pre; }
              pre.cql-diff .line.added { background: #e6ffed; }
              pre.cql-diff .line.removed { background: #ffeef0; }
              pre.cql-diff .ln { display: inline-block; width: 3.2em; text-align: right; color: #999; margin-right: 6px; user-select: none; }
              pre.cql-diff .sym { display: inline-block; width: 1em; color: #666; }
            </style>
            """;

    /** PAT-263: a self-contained HTML report of what changed between two versions of a measure. */
    public String generateDiffHtml(MeasureVersionDiff diff) {
        StringBuilder sb = new StringBuilder();
        String name = diff.getName() != null ? diff.getName() : "Measure";
        String title = esc(name) + " — v" + esc(nz(diff.getOldVersion())) + " → v" + esc(nz(diff.getNewVersion()));
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(title).append("</title>\n").append(CSS).append(DIFF_CSS).append("</head>\n<body>\n");

        sb.append("<div class=\"header\">\n  <h1>Changes: ").append(esc(name)).append("</h1>\n");
        sb.append("  <div class=\"version\">v").append(esc(nz(diff.getOldVersion()))).append(" (").append(esc(nz(diff.getOldStatus())))
                .append(") → v").append(esc(nz(diff.getNewVersion()))).append(" (").append(esc(nz(diff.getNewStatus()))).append(")</div>\n</div>\n\n");
        sb.append("<div class=\"container\">\n");

        MeasureVersionDiff.Summary sum = diff.getSummary();
        sb.append("<section id=\"summary\">\n  <h2>Summary</h2>\n");
        if (sum == null || sum.getTotal() == 0) {
            sb.append("  <p class=\"empty\">The two versions are identical.</p>\n");
        } else {
            sb.append("  <ul class=\"summary-list\">\n");
            sb.append("    <li><strong>").append(sum.getMetadata()).append("</strong> metadata field(s)</li>\n");
            sb.append("    <li><strong>").append(sum.getGroups()).append("</strong> group(s) added / removed</li>\n");
            sb.append("    <li><strong>").append(sum.getPopulations()).append("</strong> population(s)</li>\n");
            sb.append("    <li><strong>").append(sum.getStratifiers()).append("</strong> stratifier(s)</li>\n");
            sb.append("    <li><strong>").append(sum.getObservations()).append("</strong> observation(s)</li>\n");
            sb.append("    <li>CQL: ").append(diff.isCqlChanged() ? "<strong>+" + sum.getCqlAdded() + " / −" + sum.getCqlRemoved() + "</strong> line(s)" : "unchanged").append("</li>\n");
            sb.append("  </ul>\n");
        }
        sb.append("</section>\n\n");

        sb.append("<section id=\"metadata\">\n  <h2>Metadata</h2>\n");
        if (diff.getMetadata() == null || diff.getMetadata().isEmpty()) {
            sb.append("  <p class=\"empty\">No metadata changes.</p>\n");
        } else {
            sb.append("  <table>\n    <thead><tr><th>Section</th><th>Field</th><th>Change</th><th>v").append(esc(nz(diff.getOldVersion())))
                    .append("</th><th>v").append(esc(nz(diff.getNewVersion()))).append("</th></tr></thead>\n    <tbody>\n");
            for (MeasureVersionDiff.FieldChange c : diff.getMetadata()) {
                sb.append("      <tr><td>").append(esc(c.getSection())).append("</td><td>").append(esc(c.getField())).append("</td><td>")
                        .append(kind(c.getKind())).append("</td><td class=\"old\">").append(esc(nz(c.getOldValue())))
                        .append("</td><td class=\"new\">").append(esc(nz(c.getNewValue()))).append("</td></tr>\n");
            }
            sb.append("    </tbody>\n  </table>\n");
        }
        sb.append("</section>\n\n");

        sb.append("<section id=\"populations\">\n  <h2>Population Criteria</h2>\n");
        boolean anyGroup = false;
        if (diff.getGroups() != null) {
            for (MeasureVersionDiff.GroupChange g : diff.getGroups()) {
                if (MeasureVersionDiff.UNCHANGED.equals(g.getKind())) continue;
                anyGroup = true;
                sb.append("  <div class=\"group\">\n    <h3>Group ").append(esc(g.getGroupId())).append(" ").append(kind(g.getKind())).append("</h3>\n");
                if (g.getFields() != null && !g.getFields().isEmpty()) {
                    sb.append("    <table>\n      <thead><tr><th>Field</th><th>Change</th><th>Old</th><th>New</th></tr></thead>\n      <tbody>\n");
                    for (MeasureVersionDiff.FieldChange f : g.getFields()) {
                        sb.append("        <tr><td>").append(esc(f.getField())).append("</td><td>").append(kind(f.getKind()))
                                .append("</td><td class=\"old\">").append(esc(nz(f.getOldValue()))).append("</td><td class=\"new\">").append(esc(nz(f.getNewValue()))).append("</td></tr>\n");
                    }
                    sb.append("      </tbody>\n    </table>\n");
                }
                items(sb, "Populations", "Population", g.getPopulations(), true);
                items(sb, "Stratifiers", "Stratifier", g.getStratifiers(), false);
                items(sb, "Observations", "Observation", g.getObservations(), false);
                sb.append("  </div>\n");
            }
        }
        if (!anyGroup) sb.append("  <p class=\"empty\">No population criteria changes.</p>\n");
        sb.append("</section>\n\n");

        sb.append("<section id=\"cql\">\n  <h2>CQL Logic</h2>\n");
        if (!diff.isCqlChanged()) {
            sb.append("  <p class=\"empty\">CQL unchanged.</p>\n");
        } else if (diff.getCqlLines() == null) {
            sb.append("  <p>The CQL changed, but the texts are too large for a line-by-line comparison.</p>\n");
        } else {
            sb.append("  <pre class=\"cql-diff\">");
            for (MeasureVersionDiff.CqlLine line : diff.getCqlLines()) {
                String cls = "equal".equals(line.getType()) ? "equal" : line.getType();
                String sym = MeasureVersionDiff.ADDED.equals(line.getType()) ? "+" : MeasureVersionDiff.REMOVED.equals(line.getType()) ? "-" : " ";
                sb.append("<span class=\"line ").append(cls).append("\"><span class=\"ln\">").append(line.getOldLine() != null ? line.getOldLine() : "")
                        .append("</span><span class=\"ln\">").append(line.getNewLine() != null ? line.getNewLine() : "")
                        .append("</span><span class=\"sym\">").append(sym).append("</span>").append(esc(nz(line.getText()))).append("</span>\n");
            }
            sb.append("</pre>\n");
        }
        sb.append("</section>\n\n");

        sb.append("<footer>\n  <p>Generated by CQL Platform on ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("</p>\n</footer>\n");
        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    private void items(StringBuilder sb, String heading, String noun, List<MeasureVersionDiff.ItemChange> items, boolean populationLabels) {
        if (items == null || items.isEmpty()) return;
        sb.append("    <h4>").append(heading).append("</h4>\n    <table>\n      <thead><tr><th>").append(noun)
                .append("</th><th>Change</th><th>Old</th><th>New</th></tr></thead>\n      <tbody>\n");
        for (MeasureVersionDiff.ItemChange c : items) {
            String key = populationLabels ? POPULATION_LABELS.getOrDefault(c.getKey(), c.getKey()) : c.getKey();
            sb.append("        <tr><td class=\"pop-type\">").append(esc(key)).append("</td><td>").append(kind(c.getKind()))
                    .append("</td><td class=\"old\"><code>").append(esc(nz(c.getOldValue()))).append("</code>")
                    .append(c.getOldDescription() != null ? "<br><small>" + esc(c.getOldDescription()) + "</small>" : "")
                    .append("</td><td class=\"new\"><code>").append(esc(nz(c.getNewValue()))).append("</code>")
                    .append(c.getNewDescription() != null ? "<br><small>" + esc(c.getNewDescription()) + "</small>" : "")
                    .append("</td></tr>\n");
        }
        sb.append("      </tbody>\n    </table>\n");
    }

    private static String kind(String kind) {
        String k = kind == null ? "changed" : kind;
        return "<span class=\"kind kind-" + k + "\">" + k + "</span>";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void renderPopulationCriteria(StringBuilder sb, MeasureDefinition measure) {
        List<GroupDefinition> groups = measure.getGroupDefinitions();
        if (groups == null || groups.isEmpty()) {
            sb.append("  <p class=\"empty\">No population criteria defined.</p>\n");
            return;
        }

        // Improvement notation
        if (measure.getImprovementNotation() != null && !measure.getImprovementNotation().isBlank()) {
            sb.append("  <div class=\"info-box\">\n");
            sb.append("    <strong>Improvement Notation:</strong> ");
            sb.append(esc("increase".equals(measure.getImprovementNotation())
                    ? "Increase rate indicates improvement"
                    : "Decrease rate indicates improvement"));
            sb.append("\n  </div>\n");
        }

        for (int i = 0; i < groups.size(); i++) {
            GroupDefinition group = groups.get(i);
            sb.append("  <div class=\"group\">\n");
            sb.append("    <h3>Group ").append(i + 1);
            if (group.getDescription() != null && !group.getDescription().isBlank()) {
                sb.append(" — ").append(esc(group.getDescription()));
            }
            sb.append("</h3>\n");

            if (group.getScoringUnit() != null && !group.getScoringUnit().isBlank()) {
                sb.append("    <p><strong>Scoring Unit:</strong> ").append(esc(group.getScoringUnit())).append("</p>\n");
            }

            // Populations table
            if (group.getPopulations() != null && !group.getPopulations().isEmpty()) {
                sb.append("    <table>\n");
                sb.append("      <thead><tr><th>Population</th><th>CQL Expression</th><th>Description</th></tr></thead>\n");
                sb.append("      <tbody>\n");
                for (PopulationDefinition pop : group.getPopulations()) {
                    sb.append("        <tr>\n");
                    sb.append("          <td class=\"pop-type\">").append(esc(POPULATION_LABELS.getOrDefault(pop.getPopulationType(), pop.getPopulationType()))).append("</td>\n");
                    sb.append("          <td><code>").append(esc(pop.getCriteriaExpression() != null ? pop.getCriteriaExpression() : "—")).append("</code></td>\n");
                    sb.append("          <td>").append(esc(pop.getDescription() != null ? pop.getDescription() : "")).append("</td>\n");
                    sb.append("        </tr>\n");
                }
                sb.append("      </tbody>\n");
                sb.append("    </table>\n");
            }

            // Stratifiers
            if (group.getStratifiers() != null && !group.getStratifiers().isEmpty()) {
                sb.append("    <h4>Stratifiers</h4>\n");
                sb.append("    <table>\n");
                sb.append("      <thead><tr><th>ID</th><th>CQL Expression</th><th>Description</th></tr></thead>\n");
                sb.append("      <tbody>\n");
                for (StratifierDefinition strat : group.getStratifiers()) {
                    sb.append("        <tr>\n");
                    sb.append("          <td>").append(esc(strat.getStratifierId() != null ? strat.getStratifierId() : "—")).append("</td>\n");
                    // PAT-235: a multi-component stratifier lists one expression per component
                    String expression = strat.getCriteriaExpression() != null ? strat.getCriteriaExpression() : "—";
                    if (strat.hasComponents()) {
                        StringBuilder parts = new StringBuilder();
                        for (StratifierDefinition.Component component : strat.getComponents()) {
                            if (parts.length() > 0) parts.append(" × ");
                            parts.append(component.getCode()).append(": ").append(component.getCriteriaExpression());
                        }
                        expression = parts.toString();
                    }
                    sb.append("          <td><code>").append(esc(expression)).append("</code></td>\n");
                    sb.append("          <td>").append(esc(strat.getDescription() != null ? strat.getDescription() : "")).append("</td>\n");
                    sb.append("        </tr>\n");
                }
                sb.append("      </tbody>\n");
                sb.append("    </table>\n");
            }

            sb.append("  </div>\n");
        }

        // Rate Aggregation
        if (measure.getRateAggregation() != null && !measure.getRateAggregation().isBlank()) {
            sb.append("  <div class=\"info-box\">\n");
            sb.append("    <strong>Rate Aggregation:</strong> ").append(esc(measure.getRateAggregation())).append("\n");
            sb.append("  </div>\n");
        }
    }

    private void renderSupplementalData(StringBuilder sb, MeasureDefinition measure) {
        if (measure.getSupplementalData() != null && !measure.getSupplementalData().isEmpty()) {
            sb.append("  <h3>Supplemental Data Elements</h3>\n");
            sb.append("  <table>\n");
            sb.append("    <thead><tr><th>Definition</th><th>Description</th></tr></thead>\n");
            sb.append("    <tbody>\n");
            for (MeasureDefinition.SupplementalDataDef sd : measure.getSupplementalData()) {
                sb.append("      <tr><td><code>").append(esc(sd.getDefinition())).append("</code></td>");
                sb.append("<td>").append(esc(sd.getDescription() != null ? sd.getDescription() : "")).append("</td></tr>\n");
            }
            sb.append("    </tbody>\n</table>\n");
        }

        if (measure.getRiskAdjustments() != null && !measure.getRiskAdjustments().isEmpty()) {
            sb.append("  <h3>Risk Adjustment Variables</h3>\n");
            sb.append("  <table>\n");
            sb.append("    <thead><tr><th>Definition</th><th>Description</th></tr></thead>\n");
            sb.append("    <tbody>\n");
            for (MeasureDefinition.RiskAdjustmentDef ra : measure.getRiskAdjustments()) {
                sb.append("      <tr><td><code>").append(esc(ra.getDefinition())).append("</code></td>");
                sb.append("<td>").append(esc(ra.getDescription() != null ? ra.getDescription() : "")).append("</td></tr>\n");
            }
            sb.append("    </tbody>\n</table>\n");
        }

        if (measure.getRiskAdjustmentDescription() != null && !measure.getRiskAdjustmentDescription().isBlank()) {
            sb.append("  <p><strong>Risk Adjustment Description:</strong> ").append(esc(measure.getRiskAdjustmentDescription())).append("</p>\n");
        }
    }

    private void renderMetadata(StringBuilder sb, MeasureDefinition measure) {
        sb.append("  <table class=\"metadata\">\n");
        sb.append("    <tbody>\n");

        addMetaRow(sb, "Measure Name", measure.getName());
        addMetaRow(sb, "Version", measure.getVersion());
        addMetaRow(sb, "Title", measure.getTitle());
        addMetaRow(sb, "Status", STATUS_LABELS.getOrDefault(measure.getStatus(), measure.getStatus()));
        addMetaRow(sb, "Scoring Type", SCORING_LABELS.getOrDefault(measure.getScoringType(), measure.getScoringType()));
        addMetaRow(sb, "Setting", measure.getSetting());
        addMetaRow(sb, "CMS Measure ID", measure.getCmsMeasureId());
        addMetaRow(sb, "NQF Number", measure.getNqfNumber());
        addMetaRow(sb, "Measure Set", measure.getMeasureSet());
        addMetaRow(sb, "Steward", measure.getSteward());
        if (measure.getDevelopers() != null && !measure.getDevelopers().isEmpty()) {
            addMetaRow(sb, "Developers", String.join(", ", measure.getDevelopers()));
        }
        addMetaRow(sb, "Owner", measure.getOwnerUsername());
        addMetaRow(sb, "CQL Library", measure.getCqlLibraryId());

        if (measure.getReferences() != null && !measure.getReferences().isEmpty()) {
            StringBuilder refs = new StringBuilder();
            for (MeasureDefinition.MeasureReference ref : measure.getReferences()) {
                if (refs.length() > 0) refs.append("<br>");
                if (ref.getType() != null) refs.append("[").append(esc(ref.getType())).append("] ");
                refs.append(esc(ref.getReference()));
            }
            sb.append("      <tr><td><strong>References</strong></td><td>").append(refs).append("</td></tr>\n");
        }

        addMetaRow(sb, "Disclaimer", measure.getDisclaimer());
        addMetaRow(sb, "Copyright", measure.getCopyright());

        if (measure.getCreatedAt() != null) {
            addMetaRow(sb, "Created", measure.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
        if (measure.getUpdatedAt() != null) {
            addMetaRow(sb, "Last Updated", measure.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }

        sb.append("    </tbody>\n");
        sb.append("  </table>\n");
    }

    private void addMetaRow(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("      <tr><td><strong>").append(esc(label)).append("</strong></td><td>").append(esc(value)).append("</td></tr>\n");
        }
    }

    /** PAT-236: the "Measure Metadata" table has a row to show. */
    private static boolean hasStandardMetadataTable(MeasureDefinition measure) {
        return (measure.getMeasureTypes() != null && !measure.getMeasureTypes().isEmpty())
                || measure.getEffectiveStart() != null || measure.getEffectiveEnd() != null
                || measure.getMeasurementPeriodStart() != null || measure.getMeasurementPeriodEnd() != null
                || measure.getApprovalDate() != null || measure.getLastReviewDate() != null
                || Boolean.TRUE.equals(measure.getExperimental());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean hasSupplementalData(MeasureDefinition measure) {
        return (measure.getSupplementalData() != null && !measure.getSupplementalData().isEmpty())
                || (measure.getRiskAdjustments() != null && !measure.getRiskAdjustments().isEmpty())
                || (measure.getRiskAdjustmentDescription() != null && !measure.getRiskAdjustmentDescription().isBlank());
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final String CSS = """
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    color: #1a1a2e; background: #f8f9fa; line-height: 1.6;
  }
  .header {
    background: linear-gradient(135deg, #0D7377, #1B3A5C);
    color: white; padding: 2rem; text-align: center;
  }
  .header h1 { font-size: 1.8rem; margin-bottom: 0.3rem; }
  .header .version { font-size: 0.95rem; opacity: 0.85; margin-bottom: 0.5rem; }
  .meta-row { display: flex; gap: 0.5rem; justify-content: center; flex-wrap: wrap; }
  .badge {
    display: inline-block; padding: 0.2rem 0.7rem; border-radius: 12px;
    font-size: 0.8rem; font-weight: 600; background: rgba(255,255,255,0.2);
  }
  .badge-draft { background: #ffc107; color: #333; }
  .badge-active { background: #28a745; color: white; }
  .badge-retired { background: #6c757d; color: white; }
  .badge-in-review { background: #17a2b8; color: white; }
  .badge-scoring { background: rgba(255,255,255,0.25); }
  .container { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
  .toc {
    background: white; border: 1px solid #dee2e6; border-radius: 8px;
    padding: 1rem 1.5rem; margin-bottom: 1.5rem;
  }
  .toc h2 { font-size: 1rem; color: #0D7377; margin-bottom: 0.5rem; }
  .toc ul { list-style: none; padding-left: 0; }
  .toc li { padding: 0.2rem 0; }
  .toc a { color: #1B3A5C; text-decoration: none; }
  .toc a:hover { text-decoration: underline; }
  section {
    background: white; border: 1px solid #dee2e6; border-radius: 8px;
    padding: 1.5rem; margin-bottom: 1rem;
  }
  h2 { font-size: 1.3rem; color: #0D7377; margin-bottom: 0.8rem; border-bottom: 2px solid #e0f2f1; padding-bottom: 0.3rem; }
  h3 { font-size: 1.1rem; color: #1B3A5C; margin: 0.8rem 0 0.5rem; }
  h4 { font-size: 0.95rem; color: #555; margin: 0.6rem 0 0.3rem; }
  p { margin-bottom: 0.5rem; }
  .empty { color: #999; font-style: italic; }
  .info-box {
    background: #e8f5e9; border-left: 4px solid #0D7377;
    padding: 0.6rem 1rem; margin: 0.5rem 0; border-radius: 0 4px 4px 0;
  }
  table {
    width: 100%; border-collapse: collapse; margin: 0.5rem 0;
    font-size: 0.9rem;
  }
  th, td { padding: 0.5rem 0.8rem; text-align: left; border: 1px solid #dee2e6; }
  th { background: #f1f8f9; color: #1B3A5C; font-weight: 600; }
  .pop-type { font-weight: 600; white-space: nowrap; }
  code { background: #f5f5f5; padding: 0.15rem 0.4rem; border-radius: 3px; font-family: 'Fira Code', 'Consolas', monospace; font-size: 0.85em; }
  .cql-code {
    background: #1e1e1e; color: #d4d4d4; padding: 1rem; border-radius: 6px;
    overflow-x: auto; font-family: 'Fira Code', 'Consolas', monospace;
    font-size: 0.82rem; line-height: 1.5; white-space: pre;
  }
  .metadata td:first-child { width: 180px; white-space: nowrap; }
  .group { border: 1px solid #e0e0e0; border-radius: 6px; padding: 1rem; margin-bottom: 0.8rem; background: #fafafa; }
  footer { text-align: center; color: #999; font-size: 0.8rem; padding: 1rem 0; margin-top: 1rem; }
  @media print {
    body { background: white; }
    .header { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    section { break-inside: avoid; }
    .toc { break-after: page; }
  }
  @page { margin: 1.5cm; }
</style>
""";
}
