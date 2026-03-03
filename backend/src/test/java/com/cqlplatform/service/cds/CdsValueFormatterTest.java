package com.cqlplatform.service.cds;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CdsValueFormatterTest {

    private CdsValueFormatter formatter;

    @BeforeEach
    void setUp() {
        CdsResourceFormatter resourceFormatter = new CdsResourceFormatter();
        formatter = new CdsValueFormatter(resourceFormatter);
    }

    @Test
    void formatExpressionLine_booleanTrue_shouldReturnYes() {
        String result = formatter.formatExpressionLine("IsEligible", true);
        assertThat(result).isEqualTo("**IsEligible**: Yes");
    }

    @Test
    void formatExpressionLine_booleanFalse_shouldReturnNull() {
        String result = formatter.formatExpressionLine("IsEligible", false);
        assertThat(result).isNull();
    }

    @Test
    void formatExpressionLine_string() {
        String result = formatter.formatExpressionLine("PatientName", "John Doe");
        assertThat(result).isEqualTo("**PatientName**: John Doe");
    }

    @Test
    void formatExpressionLine_number() {
        String result = formatter.formatExpressionLine("Score", 42);
        assertThat(result).isEqualTo("**Score**: 42");
    }

    @Test
    void formatExpressionLine_quantity() {
        Quantity q = new Quantity().setValue(new BigDecimal("120")).setUnit("mmHg");
        String result = formatter.formatExpressionLine("BP", q);
        assertThat(result).isEqualTo("**BP**: 120 mmHg");
    }

    @Test
    void formatExpressionLine_codeableConcept() {
        CodeableConcept cc = new CodeableConcept().setText("Hypertension");
        String result = formatter.formatExpressionLine("Diagnosis", cc);
        assertThat(result).isEqualTo("**Diagnosis**: Hypertension");
    }

    @Test
    void formatExpressionLine_coding() {
        Coding coding = new Coding().setDisplay("Active");
        String result = formatter.formatExpressionLine("Status", coding);
        assertThat(result).isEqualTo("**Status**: Active");
    }

    @Test
    void formatExpressionLine_resource() {
        Observation obs = new Observation();
        obs.setId("obs-1");
        obs.getCode().setText("Blood Pressure");
        String result = formatter.formatExpressionLine("LastObs", obs);
        assertThat(result).contains("**LastObs**:");
        assertThat(result).contains("Blood Pressure");
    }

    @Test
    void formatExpressionLine_list_shouldFormatItems() {
        List<String> items = List.of("Item1", "Item2");
        String result = formatter.formatExpressionLine("Findings", items);
        assertThat(result).contains("**Findings**:");
        assertThat(result).contains("1. Item1");
        assertThat(result).contains("2. Item2");
    }

    @Test
    void formatExpressionLine_emptyList_shouldReturnNull() {
        List<Object> items = List.of();
        String result = formatter.formatExpressionLine("Findings", items);
        assertThat(result).isNull();
    }

    @Test
    void formatValue_null_shouldReturnNull() {
        assertThat(formatter.formatValue(null)).isEqualTo("null");
    }

    @Test
    void formatValue_string() {
        assertThat(formatter.formatValue("test")).isEqualTo("test");
    }

    @Test
    void formatValue_number() {
        assertThat(formatter.formatValue(42)).isEqualTo("42");
    }

    @Test
    void formatValue_boolean() {
        assertThat(formatter.formatValue(true)).isEqualTo("true");
    }

    @Test
    void formatValue_quantity() {
        Quantity q = new Quantity().setValue(new BigDecimal("99")).setUnit("mg");
        assertThat(formatter.formatValue(q)).isEqualTo("99 mg");
    }

    @Test
    void formatValue_resource() {
        Observation obs = new Observation();
        obs.setId("obs-1");
        assertThat(formatter.formatValue(obs)).isEqualTo("Observation/obs-1");
    }

    // --- XSS escape verification tests ---

    @Test
    void formatExpressionLine_string_shouldEscapeHtml() {
        String result = formatter.formatExpressionLine("Name", "<script>alert(1)</script>");
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void formatExpressionLine_string_shouldEscapeSvg() {
        String result = formatter.formatExpressionLine("Name", "<svg onload=alert(1)>");
        assertThat(result).doesNotContain("<svg");
        assertThat(result).contains("&lt;svg");
    }

    @Test
    void formatExpressionLine_codeableConcept_shouldEscapeHtml() {
        CodeableConcept cc = new CodeableConcept().setText("<img src=x onerror=alert(1)>");
        String result = formatter.formatExpressionLine("Dx", cc);
        assertThat(result).doesNotContain("<img");
        assertThat(result).contains("&lt;img");
    }

    @Test
    void formatExpressionLine_coding_shouldEscapeHtml() {
        Coding coding = new Coding().setDisplay("<b>bold</b>");
        String result = formatter.formatExpressionLine("Code", coding);
        assertThat(result).doesNotContain("<b>");
        assertThat(result).contains("&lt;b&gt;");
    }

    @Test
    void formatExpressionLine_quantity_shouldEscapeUnit() {
        Quantity q = new Quantity().setValue(new BigDecimal("10")).setUnit("<script>xss</script>");
        String result = formatter.formatExpressionLine("Dose", q);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void formatValue_string_shouldEscapeHtml() {
        assertThat(formatter.formatValue("<script>alert(1)</script>"))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void formatValue_codeableConcept_shouldEscapeHtml() {
        CodeableConcept cc = new CodeableConcept().setText("<div onclick=alert(1)>click</div>");
        assertThat(formatter.formatValue(cc))
                .doesNotContain("<div")
                .contains("&lt;div");
    }

    @Test
    void formatExpressionLine_list_shouldEscapeItems() {
        List<String> items = List.of("<script>xss</script>", "safe");
        String result = formatter.formatExpressionLine("Items", items);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
        assertThat(result).contains("safe");
    }
}
