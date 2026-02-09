package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.GroupDefinition;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.PopulationDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates HQMF R2.1 XML (Health Quality Measure Format) for CMS submission.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HqmfExportService {

    private final MeasureDefinitionService definitionService;

    private static final Map<String, String> SCORING_OIDS = Map.of(
            "proportion", "PROPOR",
            "ratio", "RATIO",
            "continuous-variable", "CONTVAR",
            "cohort", "COHORT"
    );

    private static final Map<String, String> POPULATION_CODES = Map.of(
            "initial-population", "IPP",
            "denominator", "DENOM",
            "denominator-exclusion", "DENEX",
            "denominator-exception", "DENEXCEP",
            "numerator", "NUMER",
            "numerator-exclusion", "NUMEX",
            "measure-population", "MSRPOPL",
            "measure-population-exclusion", "MSRPOPLEX"
    );

    public String exportHqmf(Long measureId) {
        MeasureDefinition def = definitionService.getById(measureId)
                .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + measureId));

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<QualityMeasureDocument xmlns=\"urn:hl7-org:v3\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        // Header
        xml.append("  <typeId root=\"2.16.840.1.113883.1.3\" extension=\"POQM_HD000001UV02\"/>\n");
        xml.append("  <templateId root=\"2.16.840.1.113883.10.20.28.1.2\"/>\n");
        xml.append("  <id root=\"").append(esc(UUID.randomUUID().toString())).append("\"/>\n");
        xml.append("  <code code=\"57024-2\" codeSystem=\"2.16.840.1.113883.6.1\" displayName=\"Health Quality Measure Document\"/>\n");
        xml.append("  <title>").append(esc(def.getTitle() != null ? def.getTitle() : def.getName())).append("</title>\n");
        xml.append("  <text>").append(esc(def.getDescription() != null ? def.getDescription() : "")).append("</text>\n");
        xml.append("  <statusCode code=\"").append(esc(def.getStatus() != null ? def.getStatus() : "draft")).append("\"/>\n");
        xml.append("  <setId root=\"").append(esc(def.getMeasureSet() != null ? def.getMeasureSet() : UUID.randomUUID().toString())).append("\"/>\n");
        xml.append("  <versionNumber value=\"").append(esc(def.getVersion())).append("\"/>\n");

        // Measure attributes (scoring)
        xml.append("  <subjectOf>\n");
        xml.append("    <measureAttribute>\n");
        xml.append("      <code code=\"MSRSCORE\" codeSystem=\"2.16.840.1.113883.5.4\">\n");
        xml.append("        <originalText>").append(esc(def.getScoringType())).append("</originalText>\n");
        xml.append("      </code>\n");
        String scoringCode = SCORING_OIDS.getOrDefault(def.getScoringType(), "PROPOR");
        xml.append("      <value xsi:type=\"CD\" code=\"").append(scoringCode).append("\" codeSystem=\"2.16.840.1.113883.1.11.20367\"/>\n");
        xml.append("    </measureAttribute>\n");
        xml.append("  </subjectOf>\n");

        // Steward
        if (def.getSteward() != null) {
            xml.append("  <subjectOf>\n");
            xml.append("    <measureAttribute>\n");
            xml.append("      <code code=\"MSRSTEWARD\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
            xml.append("      <value xsi:type=\"ED\">").append(esc(def.getSteward())).append("</value>\n");
            xml.append("    </measureAttribute>\n");
            xml.append("  </subjectOf>\n");
        }

        // Population Criteria Section
        xml.append("  <component>\n");
        xml.append("    <populationCriteriaSection>\n");
        xml.append("      <templateId root=\"2.16.840.1.113883.10.20.28.2.7\"/>\n");
        xml.append("      <code code=\"57026-7\" codeSystem=\"2.16.840.1.113883.6.1\"/>\n");
        xml.append("      <title>Population Criteria Section</title>\n");

        List<GroupDefinition> groups = def.getGroupDefinitions() != null ? def.getGroupDefinitions() : List.of();
        for (GroupDefinition group : groups) {
            if (group.getPopulations() == null) continue;
            for (PopulationDefinition pop : group.getPopulations()) {
                String popCode = POPULATION_CODES.getOrDefault(pop.getPopulationType(), "IPP");
                String popUuid = UUID.randomUUID().toString();
                xml.append("      <component>\n");
                xml.append("        <").append(getHqmfPopulationElement(pop.getPopulationType())).append(">\n");
                xml.append("          <id root=\"").append(popUuid).append("\" extension=\"").append(popCode).append("\"/>\n");
                if (pop.getCriteriaExpression() != null) {
                    xml.append("          <precondition>\n");
                    xml.append("            <criteriaReference moodCode=\"EVN\">\n");
                    xml.append("              <id root=\"").append(esc(pop.getCriteriaExpression())).append("\"/>\n");
                    xml.append("            </criteriaReference>\n");
                    xml.append("          </precondition>\n");
                }
                xml.append("        </").append(getHqmfPopulationElement(pop.getPopulationType())).append(">\n");
                xml.append("      </component>\n");
            }
        }
        xml.append("    </populationCriteriaSection>\n");
        xml.append("  </component>\n");

        // CQL Library Section
        if (def.getCqlContent() != null) {
            xml.append("  <component>\n");
            xml.append("    <cqlLibrary>\n");
            xml.append("      <text mediaType=\"text/cql\">\n");
            xml.append("        ").append(Base64.getEncoder().encodeToString(
                    def.getCqlContent().getBytes(StandardCharsets.UTF_8))).append("\n");
            xml.append("      </text>\n");
            xml.append("    </cqlLibrary>\n");
            xml.append("  </component>\n");
        }

        xml.append("</QualityMeasureDocument>\n");
        return xml.toString();
    }

    private String getHqmfPopulationElement(String populationType) {
        return switch (populationType) {
            case "initial-population" -> "initialPopulationCriteria";
            case "denominator" -> "denominatorCriteria";
            case "denominator-exclusion" -> "denominatorExclusionCriteria";
            case "denominator-exception" -> "denominatorExceptionCriteria";
            case "numerator" -> "numeratorCriteria";
            case "numerator-exclusion" -> "numeratorExclusionCriteria";
            case "measure-population" -> "measurePopulationCriteria";
            case "measure-population-exclusion" -> "measurePopulationExclusionCriteria";
            default -> "initialPopulationCriteria";
        };
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
