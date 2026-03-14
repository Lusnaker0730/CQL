package com.cqlplatform.service.measure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shifts all date/dateTime/instant fields in a FHIR Bundle JSON by a given number of days.
 * Useful for adjusting test case patient data to fall within a measure's evaluation period.
 */
@Service
@Slf4j
public class DateShiftService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // FHIR date formats: YYYY, YYYY-MM, YYYY-MM-DD
    private static final Pattern FHIR_DATE_PATTERN = Pattern.compile(
            "^\\d{4}(-\\d{2}(-\\d{2})?)?$");

    // FHIR dateTime/instant: YYYY-MM-DDThh:mm:ss[.sss][Z|+/-hh:mm]
    private static final Pattern FHIR_DATETIME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$");

    // Known FHIR date/dateTime/instant field names
    private static final Set<String> DATE_FIELD_NAMES = Set.of(
            // Common date fields
            "date", "birthDate", "deceasedDateTime", "issued", "authored", "recorded",
            "effectiveDateTime", "onsetDateTime", "abatementDateTime", "performedDateTime",
            "occurrenceDateTime", "authoredOn", "sent", "received", "assertedDate",
            "recordedDate", "dateAsserted", "lastUpdated", "created", "dateTime",
            // Period fields (start/end handled via period object traversal)
            "start", "end",
            // Timing fields
            "event",
            // instant fields
            "lastModified", "timestamp"
    );

    /**
     * Shift all recognized date/dateTime fields in the given FHIR Bundle JSON by the specified days.
     *
     * @param bundleJson  the FHIR Bundle JSON string
     * @param shiftDays   number of days to shift (positive = forward, negative = backward)
     * @return the shifted Bundle JSON string
     */
    public String shiftDates(String bundleJson, int shiftDays) {
        if (bundleJson == null || bundleJson.isBlank() || shiftDays == 0) {
            return bundleJson;
        }
        try {
            JsonNode root = MAPPER.readTree(bundleJson);
            if (root.isObject()) {
                shiftNode((ObjectNode) root, null, shiftDays);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to shift dates in bundle JSON, returning original", e);
            return bundleJson;
        }
    }

    /**
     * Calculate the number of days to shift dates so that the latest date in the bundle
     * falls within the target measurement period.
     *
     * @param bundleJson       the FHIR Bundle JSON
     * @param targetPeriodEnd  the end date of the target measurement period
     * @return the number of days to shift, or 0 if no dates found
     */
    public int calculateAutoShift(String bundleJson, LocalDate targetPeriodEnd) {
        if (bundleJson == null || bundleJson.isBlank() || targetPeriodEnd == null) {
            return 0;
        }
        try {
            JsonNode root = MAPPER.readTree(bundleJson);
            LocalDate latestDate = findLatestDate(root);
            if (latestDate == null) return 0;
            return (int) ChronoUnit.DAYS.between(latestDate, targetPeriodEnd);
        } catch (Exception e) {
            log.warn("Failed to calculate auto-shift", e);
            return 0;
        }
    }

    private void shiftNode(ObjectNode node, String parentFieldName, int shiftDays) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isTextual() && isDateField(fieldName)) {
                String shifted = shiftDateString(value.asText(), shiftDays);
                if (shifted != null) {
                    node.set(fieldName, new TextNode(shifted));
                }
            } else if (value.isObject()) {
                shiftNode((ObjectNode) value, fieldName, shiftDays);
            } else if (value.isArray()) {
                shiftArray((ArrayNode) value, fieldName, shiftDays);
            }
        }
    }

    private void shiftArray(ArrayNode array, String fieldName, int shiftDays) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode element = array.get(i);
            if (element.isObject()) {
                shiftNode((ObjectNode) element, fieldName, shiftDays);
            } else if (element.isTextual() && isDateField(fieldName)) {
                String shifted = shiftDateString(element.asText(), shiftDays);
                if (shifted != null) {
                    array.set(i, new TextNode(shifted));
                }
            }
        }
    }

    private boolean isDateField(String fieldName) {
        return DATE_FIELD_NAMES.contains(fieldName);
    }

    /**
     * Attempt to parse and shift a FHIR date/dateTime/instant string.
     * Returns the shifted string or null if the value is not a recognized date format.
     */
    private String shiftDateString(String value, int shiftDays) {
        if (value == null || value.isEmpty()) return null;

        // Try full dateTime with offset: 2024-01-15T10:30:00+08:00
        if (FHIR_DATETIME_PATTERN.matcher(value).matches()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(value);
                return odt.plusDays(shiftDays).toString();
            } catch (DateTimeParseException e) {
                // Try without offset (plain LocalDateTime)
                try {
                    LocalDateTime ldt = LocalDateTime.parse(value);
                    return ldt.plusDays(shiftDays).toString();
                } catch (DateTimeParseException e2) {
                    // Fall through
                }
            }
        }

        // Try date only: 2024-01-15
        if (FHIR_DATE_PATTERN.matcher(value).matches()) {
            if (value.length() == 10) {
                // Full date: YYYY-MM-DD
                try {
                    LocalDate date = LocalDate.parse(value);
                    return date.plusDays(shiftDays).toString();
                } catch (DateTimeParseException e) {
                    return null;
                }
            } else if (value.length() == 7) {
                // Year-month: YYYY-MM — shift by months approximation
                try {
                    LocalDate date = LocalDate.parse(value + "-01");
                    LocalDate shifted = date.plusDays(shiftDays);
                    return shifted.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
            // Year only: YYYY — don't shift
            return null;
        }

        return null;
    }

    private LocalDate findLatestDate(JsonNode node) {
        LocalDate latest = null;

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();

                if (value.isTextual() && isDateField(fieldName)) {
                    LocalDate parsed = parseDateOnly(value.asText());
                    if (parsed != null && (latest == null || parsed.isAfter(latest))) {
                        latest = parsed;
                    }
                } else if (value.isObject() || value.isArray()) {
                    LocalDate found = findLatestDate(value);
                    if (found != null && (latest == null || found.isAfter(latest))) {
                        latest = found;
                    }
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                LocalDate found = findLatestDate(element);
                if (found != null && (latest == null || found.isAfter(latest))) {
                    latest = found;
                }
            }
        }

        return latest;
    }

    private LocalDate parseDateOnly(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10));
            }
        } catch (DateTimeParseException e) {
            // ignore
        }
        return null;
    }
}
