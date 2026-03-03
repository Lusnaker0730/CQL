package com.cqlplatform.service.cds;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Formats CQL expression results (various types) into display strings
 * suitable for CDS card detail text.
 */
@Component
@RequiredArgsConstructor
public class CdsValueFormatter {

    private final CdsResourceFormatter resourceFormatter;

    /** HTML-escape a string to prevent XSS when rendered in CDS card output. */
    private static String esc(String v) {
        return v == null ? null : HtmlUtils.htmlEscape(v);
    }

    /**
     * Format a single CQL expression result as a markdown line for the consolidated card.
     * Returns null for values that should be skipped (e.g., false booleans).
     * All FHIR/CQL-derived values are HTML-escaped for safe rendering.
     */
    public String formatExpressionLine(String exprName, Object value) {
        if (value instanceof Boolean) {
            if ((Boolean) value) {
                return "**" + exprName + "**: Yes";
            }
            return null; // Skip false booleans
        }
        if (value instanceof String) {
            return "**" + exprName + "**: " + esc((String) value);
        }
        if (value instanceof Number) {
            return "**" + exprName + "**: " + value;
        }
        if (value instanceof java.time.temporal.Temporal || value instanceof java.util.Date) {
            return "**" + exprName + "**: " + value;
        }
        if (value instanceof Quantity q) {
            String display = q.getValue() + (q.getUnit() != null ? " " + esc(q.getUnit()) : "");
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof CodeableConcept cc) {
            String display = cc.hasText() ? esc(cc.getText())
                    : (cc.hasCoding() ? esc(cc.getCodingFirstRep().getDisplay()) : esc(cc.toString()));
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof Coding coding) {
            String display = coding.hasDisplay() ? esc(coding.getDisplay())
                    : esc(coding.getSystem()) + "|" + esc(coding.getCode());
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof Period p) {
            String display = (p.hasStart() ? p.getStart().toString() : "?")
                    + " to " + (p.hasEnd() ? p.getEnd().toString() : "?");
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof Resource res) {
            return "**" + exprName + "**: " + resourceFormatter.formatDetail(res);
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append("**").append(exprName).append("**:");
            int count = 0;
            for (Object item : (Iterable<?>) value) {
                if (item == null)
                    continue;
                count++;
                sb.append("\n  ").append(count).append(". ").append(formatValue(item));
            }
            return count > 0 ? sb.toString() : null;
        }
        if (value.getClass().getSimpleName().contains("Interval")) {
            return "**" + exprName + "**: " + esc(value.toString());
        }
        if (value instanceof PrimitiveType) {
            return "**" + exprName + "**: " + esc(((PrimitiveType<?>) value).getValueAsString());
        }
        return "**" + exprName + "**: " + esc(value.toString());
    }

    /**
     * Format a single value for display (used in list items, tuple field values, etc.).
     * All FHIR/CQL-derived values are HTML-escaped for safe rendering.
     */
    public String formatValue(Object value) {
        if (value == null)
            return "null";
        if (value instanceof String) {
            return esc((String) value);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof java.time.temporal.Temporal)
            return value.toString();
        if (value instanceof Quantity q) {
            return q.getValue() + (q.getUnit() != null ? " " + esc(q.getUnit()) : "");
        }
        if (value instanceof CodeableConcept cc) {
            return cc.hasText() ? esc(cc.getText())
                    : (cc.hasCoding() ? esc(cc.getCodingFirstRep().getDisplay()) : esc(cc.toString()));
        }
        if (value instanceof Coding c) {
            return c.hasDisplay() ? esc(c.getDisplay()) : esc(c.getSystem()) + "|" + esc(c.getCode());
        }
        if (value instanceof PrimitiveType) {
            return esc(((PrimitiveType<?>) value).getValueAsString());
        }
        if (value instanceof Resource res) {
            return resourceFormatter.formatDetail(res);
        }
        return esc(value.toString());
    }
}
