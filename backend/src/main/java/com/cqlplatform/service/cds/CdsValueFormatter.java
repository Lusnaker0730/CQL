package com.cqlplatform.service.cds;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/**
 * Formats CQL expression results (various types) into display strings
 * suitable for CDS card detail text.
 * Note: No HTML escaping is needed here — the frontend (React) auto-escapes
 * text content in JSX, so additional escaping causes literal entities like &#39;
 */
@Component
@RequiredArgsConstructor
public class CdsValueFormatter {

    private final CdsResourceFormatter resourceFormatter;

    /**
     * Format a single CQL expression result as a markdown line for the consolidated card.
     * Returns null for values that should be skipped (e.g., false booleans).
     * Values are returned as-is; the frontend handles escaping.
     */
    public String formatExpressionLine(String exprName, Object value) {
        if (value instanceof Boolean) {
            if ((Boolean) value) {
                return "**" + exprName + "**: Yes";
            }
            return null; // Skip false booleans
        }
        if (value instanceof String) {
            return "**" + exprName + "**: " + (String) value;
        }
        if (value instanceof Number) {
            return "**" + exprName + "**: " + value;
        }
        if (value instanceof java.time.temporal.Temporal || value instanceof java.util.Date) {
            return "**" + exprName + "**: " + value;
        }
        if (value instanceof Quantity q) {
            String display = q.getValue() + (q.getUnit() != null ? " " + q.getUnit() : "");
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof CodeableConcept cc) {
            String display = cc.hasText() ? cc.getText()
                    : (cc.hasCoding() ? cc.getCodingFirstRep().getDisplay() : cc.toString());
            return "**" + exprName + "**: " + display;
        }
        if (value instanceof Coding coding) {
            String display = coding.hasDisplay() ? coding.getDisplay()
                    : coding.getSystem() + "|" + coding.getCode();
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
            return "**" + exprName + "**: " + value.toString();
        }
        if (value instanceof PrimitiveType) {
            return "**" + exprName + "**: " + ((PrimitiveType<?>) value).getValueAsString();
        }
        return "**" + exprName + "**: " + value.toString();
    }

    /**
     * Format a single value for display (used in list items, tuple field values, etc.).
     * Values are returned as-is; the frontend handles escaping.
     */
    public String formatValue(Object value) {
        if (value == null)
            return "null";
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof java.time.temporal.Temporal)
            return value.toString();
        if (value instanceof Quantity q) {
            return q.getValue() + (q.getUnit() != null ? " " + q.getUnit() : "");
        }
        if (value instanceof CodeableConcept cc) {
            return cc.hasText() ? cc.getText()
                    : (cc.hasCoding() ? cc.getCodingFirstRep().getDisplay() : cc.toString());
        }
        if (value instanceof Coding c) {
            return c.hasDisplay() ? c.getDisplay() : c.getSystem() + "|" + c.getCode();
        }
        if (value instanceof PrimitiveType) {
            return ((PrimitiveType<?>) value).getValueAsString();
        }
        if (value instanceof Resource res) {
            return resourceFormatter.formatDetail(res);
        }
        return value.toString();
    }
}
