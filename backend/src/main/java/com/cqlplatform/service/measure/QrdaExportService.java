package com.cqlplatform.service.measure;

import com.cqlplatform.entity.MeasureReportEntity;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.MeasureEvaluationResult.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import com.cqlplatform.util.IdGenerator;

/**
 * Generates QRDA Category III XML (aggregate quality report) from MeasureReportEntity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrdaExportService {

    private static final Map<String, String> POPULATION_TYPE_OIDS = Map.of(
            "initial-population", "2.16.840.1.113883.10.20.27.3.14",
            "denominator", "2.16.840.1.113883.10.20.27.3.15",
            "denominator-exclusion", "2.16.840.1.113883.10.20.27.3.17",
            "denominator-exception", "2.16.840.1.113883.10.20.27.3.18",
            "numerator", "2.16.840.1.113883.10.20.27.3.16",
            "numerator-exclusion", "2.16.840.1.113883.10.20.27.3.19"
    );

    public String exportQrdaIII(MeasureReportEntity report) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ClinicalDocument xmlns=\"urn:hl7-org:v3\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        xml.append(" xmlns:voc=\"urn:hl7-org:v3/voc\" xmlns:sdtc=\"urn:hl7-org:sdtc\">\n");

        // Header
        xml.append("  <realmCode code=\"US\"/>\n");
        xml.append("  <typeId root=\"2.16.840.1.113883.1.3\" extension=\"POCD_HD000040\"/>\n");
        xml.append("  <templateId root=\"2.16.840.1.113883.10.20.27.1.1\" extension=\"2024-05-01\"/>\n");
        xml.append("  <id root=\"").append(esc(IdGenerator.uuid())).append("\"/>\n");
        xml.append("  <code code=\"55184-6\" codeSystem=\"2.16.840.1.113883.6.1\" displayName=\"Quality Reporting Document Architecture Category III\"/>\n");
        xml.append("  <title>QRDA III Report - ").append(esc(report.getMeasureName())).append("</title>\n");
        xml.append("  <effectiveTime value=\"").append(formatDateTime(LocalDateTime.now())).append("\"/>\n");
        xml.append("  <confidentialityCode code=\"N\" codeSystem=\"2.16.840.1.113883.5.25\"/>\n");
        xml.append("  <languageCode code=\"en\"/>\n");

        // Record target (aggregate — no specific patient)
        xml.append("  <recordTarget>\n");
        xml.append("    <patientRole>\n");
        xml.append("      <id nullFlavor=\"NA\"/>\n");
        xml.append("    </patientRole>\n");
        xml.append("  </recordTarget>\n");

        // Author
        xml.append("  <author>\n");
        xml.append("    <time value=\"").append(formatDateTime(LocalDateTime.now())).append("\"/>\n");
        xml.append("    <assignedAuthor>\n");
        xml.append("      <id root=\"2.16.840.1.113883.4.6\" extension=\"CQL-Platform\"/>\n");
        xml.append("    </assignedAuthor>\n");
        xml.append("  </author>\n");

        // Custodian
        xml.append("  <custodian>\n");
        xml.append("    <assignedCustodian>\n");
        xml.append("      <representedCustodianOrganization>\n");
        xml.append("        <id root=\"2.16.840.1.113883.4.6\"/>\n");
        xml.append("        <name>CQL Platform</name>\n");
        xml.append("      </representedCustodianOrganization>\n");
        xml.append("    </assignedCustodian>\n");
        xml.append("  </custodian>\n");

        // Reporting Period
        xml.append("  <documentationOf>\n");
        xml.append("    <serviceEvent classCode=\"PCPR\">\n");
        xml.append("      <effectiveTime>\n");
        xml.append("        <low value=\"").append(esc(report.getPeriodStart().toString().replace("-", ""))).append("\"/>\n");
        xml.append("        <high value=\"").append(esc(report.getPeriodEnd().toString().replace("-", ""))).append("\"/>\n");
        xml.append("      </effectiveTime>\n");
        xml.append("    </serviceEvent>\n");
        xml.append("  </documentationOf>\n");

        // Body
        xml.append("  <component>\n");
        xml.append("    <structuredBody>\n");

        // Measure Section
        xml.append("      <component>\n");
        xml.append("        <section>\n");
        xml.append("          <templateId root=\"2.16.840.1.113883.10.20.27.2.1\"/>\n");
        xml.append("          <code code=\"55186-1\" codeSystem=\"2.16.840.1.113883.6.1\"/>\n");
        xml.append("          <title>Measure Section</title>\n");

        MeasureEvaluationResult result = report.getEvaluationResult();
        if (result != null && result.getGroups() != null) {
            for (GroupResult group : result.getGroups()) {
                xml.append("          <entry>\n");
                xml.append("            <organizer classCode=\"CLUSTER\" moodCode=\"EVN\">\n");
                xml.append("              <templateId root=\"2.16.840.1.113883.10.20.27.3.1\"/>\n");
                xml.append("              <statusCode code=\"completed\"/>\n");

                // Measure reference
                xml.append("              <reference typeCode=\"REFR\">\n");
                xml.append("                <externalDocument classCode=\"DOC\" moodCode=\"EVN\">\n");
                xml.append("                  <id root=\"").append(esc(report.getMeasureName())).append("\"/>\n");
                xml.append("                </externalDocument>\n");
                xml.append("              </reference>\n");

                // Performance Rate
                if (group.getMeasureScore() != null) {
                    xml.append("              <component>\n");
                    xml.append("                <observation classCode=\"OBS\" moodCode=\"EVN\">\n");
                    xml.append("                  <templateId root=\"2.16.840.1.113883.10.20.27.3.14\"/>\n");
                    xml.append("                  <code code=\"MSRAGG\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
                    xml.append("                  <statusCode code=\"completed\"/>\n");
                    xml.append("                  <value xsi:type=\"REAL\" value=\"").append(String.format("%.4f", group.getMeasureScore() / 100.0)).append("\"/>\n");
                    xml.append("                </observation>\n");
                    xml.append("              </component>\n");
                }

                // Population counts
                if (group.getPopulations() != null) {
                    for (PopulationResult pop : group.getPopulations()) {
                        String templateOid = POPULATION_TYPE_OIDS.getOrDefault(pop.getPopulationType(), "2.16.840.1.113883.10.20.27.3.16");
                        xml.append("              <component>\n");
                        xml.append("                <observation classCode=\"OBS\" moodCode=\"EVN\">\n");
                        xml.append("                  <templateId root=\"").append(templateOid).append("\"/>\n");
                        xml.append("                  <code code=\"ASSERTION\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
                        xml.append("                  <statusCode code=\"completed\"/>\n");
                        xml.append("                  <value xsi:type=\"CD\" code=\"").append(esc(pop.getPopulationType())).append("\"/>\n");
                        xml.append("                  <entryRelationship typeCode=\"SUBJ\" inversionInd=\"true\">\n");
                        xml.append("                    <observation classCode=\"OBS\" moodCode=\"EVN\">\n");
                        xml.append("                      <templateId root=\"2.16.840.1.113883.10.20.27.3.3\"/>\n");
                        xml.append("                      <code code=\"MSRAGG\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
                        xml.append("                      <statusCode code=\"completed\"/>\n");
                        xml.append("                      <value xsi:type=\"INT\" value=\"").append(pop.getCount() != null ? pop.getCount() : 0).append("\"/>\n");
                        xml.append("                    </observation>\n");
                        xml.append("                  </entryRelationship>\n");
                        xml.append("                </observation>\n");
                        xml.append("              </component>\n");
                    }
                }

                // Stratifier results
                if (group.getStratifiers() != null) {
                    for (StratifierResult strat : group.getStratifiers()) {
                        xml.append("              <component>\n");
                        xml.append("                <observation classCode=\"OBS\" moodCode=\"EVN\">\n");
                        xml.append("                  <templateId root=\"2.16.840.1.113883.10.20.27.3.4\"/>\n");
                        xml.append("                  <code code=\"STRATIFICATION\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
                        xml.append("                  <statusCode code=\"completed\"/>\n");
                        xml.append("                  <value xsi:type=\"CD\" code=\"").append(esc(strat.getStrataValue())).append("\"/>\n");
                        if (strat.getMeasureScore() != null) {
                            xml.append("                  <entryRelationship typeCode=\"SUBJ\">\n");
                            xml.append("                    <observation classCode=\"OBS\" moodCode=\"EVN\">\n");
                            xml.append("                      <code code=\"MSRAGG\" codeSystem=\"2.16.840.1.113883.5.4\"/>\n");
                            xml.append("                      <value xsi:type=\"REAL\" value=\"").append(String.format("%.4f", strat.getMeasureScore() / 100.0)).append("\"/>\n");
                            xml.append("                    </observation>\n");
                            xml.append("                  </entryRelationship>\n");
                        }
                        xml.append("                </observation>\n");
                        xml.append("              </component>\n");
                    }
                }

                xml.append("            </organizer>\n");
                xml.append("          </entry>\n");
            }
        }

        xml.append("        </section>\n");
        xml.append("      </component>\n");
        xml.append("    </structuredBody>\n");
        xml.append("  </component>\n");
        xml.append("</ClinicalDocument>\n");

        return xml.toString();
    }

    private String formatDateTime(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
