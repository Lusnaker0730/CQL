package com.cqlplatform.service.measure;

import com.cqlplatform.testsupport.Fuzz;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PAT-265 — {@link DateShiftService} under random FHIR-shaped JSON: date fields of every precision
 * ({@code YYYY}, {@code YYYY-MM}, {@code YYYY-MM-DD}, dateTimes with an offset, {@code Z}, fractional
 * seconds, or no zone) anywhere in nested objects and arrays, next to look-alike strings under
 * non-date keys. Shifting by days or years and back must restore the document; every recognised date
 * must move by exactly the shift; nothing else may change; and a shifted dateTime must still be a
 * valid FHIR dateTime — which is where this test found BUG-150 ({@code OffsetDateTime.toString()}
 * drops {@code :00} seconds, so a shifted {@code 10:30:00+08:00} came back as {@code 10:30+08:00}).
 */
@DisplayName("PAT-265 — date shift properties")
class DateShiftServicePropertyTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> DATE_KEYS = List.of(
            "date", "birthDate", "deceasedDateTime", "issued", "authored", "recorded", "effectiveDateTime", "onsetDateTime",
            "abatementDateTime", "performedDateTime", "occurrenceDateTime", "authoredOn", "sent", "received", "recordedDate",
            "lastUpdated", "created", "dateTime", "start", "end", "event", "lastModified", "timestamp");
    private static final Set<String> DATE_KEY_SET = Set.copyOf(DATE_KEYS);
    private static final List<String> OTHER_KEYS = List.of("note", "code", "status", "id", "text", "display", "valueString", "resourceType");
    private static final Pattern FHIR_DATETIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?$");
    private static final Pattern DATETIME_LIKE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T.*");

    private final DateShiftService service = new DateShiftService();

    enum Form { YEAR, YEAR_MONTH, DATE, DATETIME_OFFSET, DATETIME_Z, DATETIME_FRACTION, DATETIME_LOCAL }

    static String randomDate(Random r, boolean allowYearMonth) {
        int y = Fuzz.between(r, 1950, 2030);
        int m = Fuzz.between(r, 1, 12);
        int d = Fuzz.between(r, 1, 28);   // every month has these; 29 Feb would not round-trip a year shift
        String time = String.format("T%02d:%02d:%02d", Fuzz.between(r, 0, 23), Fuzz.between(r, 0, 59), Fuzz.chance(r, 0.4) ? 0 : Fuzz.between(r, 0, 59));
        Form form = Fuzz.pick(r, Form.values());
        if (form == Form.YEAR_MONTH && !allowYearMonth) form = Form.DATE;
        return switch (form) {
            case YEAR -> String.format("%04d", y);
            case YEAR_MONTH -> String.format("%04d-%02d", y, m);
            case DATE -> String.format("%04d-%02d-%02d", y, m, d);
            case DATETIME_OFFSET -> String.format("%04d-%02d-%02d%s%s", y, m, d, time, Fuzz.pick(r, "+08:00", "-05:00", "+00:00"));
            case DATETIME_Z -> String.format("%04d-%02d-%02d%sZ", y, m, d, time);
            case DATETIME_FRACTION -> String.format("%04d-%02d-%02d%s.%03d+08:00", y, m, d, time, Fuzz.between(r, 0, 999));
            case DATETIME_LOCAL -> String.format("%04d-%02d-%02d%s", y, m, d, time);
        };
    }

    static ObjectNode randomDocument(Random r, int depth, boolean allowYearMonth) {
        ObjectNode node = JSON.createObjectNode();
        int fields = Fuzz.between(r, 1, 6);
        for (int i = 0; i < fields; i++) {
            switch (Fuzz.between(r, 0, depth > 2 ? 3 : 6)) {
                case 0, 1 -> node.put(Fuzz.pick(r, DATE_KEYS), randomDate(r, allowYearMonth));
                case 2 -> node.put(Fuzz.pick(r, OTHER_KEYS), Fuzz.pick(r, randomDate(r, true), "final", "2025-13-45", "", "T10:00"));
                case 3 -> node.put(Fuzz.pick(r, DATE_KEYS), Fuzz.pick(r, "unknown", "", "2025-02-30T25:00:00Z", "yesterday"));
                case 4 -> node.set(Fuzz.pick(r, "period", "effectivePeriod", "valuePeriod", "contained", "resource"), randomDocument(r, depth + 1, allowYearMonth));
                case 5 -> {
                    ArrayNode array = JSON.createArrayNode();
                    int n = Fuzz.between(r, 0, 3);
                    for (int k = 0; k < n; k++) array.add(randomDocument(r, depth + 1, allowYearMonth));
                    node.set(Fuzz.pick(r, "entry", "extension", "identifier"), array);
                }
                default -> {
                    ArrayNode array = JSON.createArrayNode();
                    int n = Fuzz.between(r, 0, 3);
                    for (int k = 0; k < n; k++) array.add(randomDate(r, allowYearMonth));
                    node.set(Fuzz.pick(r, "event", "note"), array);
                }
            }
        }
        return node;
    }

    @Test
    @DisplayName("a day shift and its inverse restore the document; every full date moves by the shift; nothing else changes")
    void shiftDays_roundTripsAndMovesOnlyDates() {
        Fuzz.forAll(20261060L, 400, r -> Map.entry(randomDocument(r, 0, false), (r.nextBoolean() ? 1 : -1) * Fuzz.between(r, 1, 3000)), (input, r) -> {
            String original = JSON.writeValueAsString(input.getKey());
            int days = input.getValue();
            String shifted = service.shiftDates(original, days);
            String back = service.shiftDates(shifted, -days);
            compare(JSON.readTree(original), JSON.readTree(shifted), null, Period.ofDays(days));
            compare(JSON.readTree(original), JSON.readTree(back), null, Period.ZERO);
            assertThat(service.shiftDates(original, 0)).isSameAs(original);
        });
    }

    @Test
    @DisplayName("a year shift and its inverse restore the document; every date, however partial, moves by whole years")
    void shiftYears_roundTripsAndMovesEveryDate() {
        Fuzz.forAll(20261061L, 400, r -> Map.entry(randomDocument(r, 0, true), (r.nextBoolean() ? 1 : -1) * Fuzz.between(r, 1, 100)), (input, r) -> {
            String original = JSON.writeValueAsString(input.getKey());
            int years = input.getValue();
            String shifted = service.shiftYears(original, years);
            String back = service.shiftYears(shifted, -years);
            compare(JSON.readTree(original), JSON.readTree(shifted), null, Period.ofYears(years));
            compare(JSON.readTree(original), JSON.readTree(back), null, Period.ZERO);
        });
    }

    @Test
    @DisplayName("non-JSON input: a day shift hands it back, a year shift refuses it")
    void nonJson() {
        Fuzz.forAll(20261062L, 50, r -> Fuzz.pick(r, "not json", "[1,2]", "\"2025-01-01\"", "{", " "), (text, r) -> {
            assertThat(service.shiftDates(text, 7)).isEqualTo(text);
            if (text.isBlank()) {
                assertThat(service.shiftYears(text, 1)).isEqualTo(text);
            } else {
                assertThatThrownBy(() -> service.shiftYears(text, 1)).isInstanceOf(IllegalArgumentException.class);
            }
        });
    }

    // ───────────────────────────────────────────────────────────── the model

    /**
     * Walks both trees in step. A text under a date key that the service recognises must equal the
     * original moved by {@code period}; every other value must be identical. Date keys are the
     * service's own list, so the structure of a FHIR bundle is what decides.
     */
    private static void compare(JsonNode original, JsonNode actual, String key, Period period) {
        assertThat(actual.getNodeType()).as("node type at " + key).isEqualTo(original.getNodeType());
        if (original.isObject()) {
            assertThat(fieldNames(actual)).as("keys at " + key).isEqualTo(fieldNames(original));
            for (String name : fieldNames(original)) compare(original.get(name), actual.get(name), name, period);
        } else if (original.isArray()) {
            assertThat(actual.size()).as("array size at " + key).isEqualTo(original.size());
            for (int i = 0; i < original.size(); i++) compare(original.get(i), actual.get(i), key, period);
        } else if (original.isTextual() && key != null && DATE_KEY_SET.contains(key)) {
            compareDate(original.asText(), actual.asText(), period, key);
        } else {
            assertThat(actual).as("untouched value at " + key).isEqualTo(original);
        }
    }

    private static void compareDate(String original, String actual, Period period, String key) {
        String context = key + ": " + original + " -> " + actual + " by " + period;
        if (DATETIME_LIKE.matcher(original).matches() && FHIR_DATETIME.matcher(original).matches()) {
            boolean zoned = original.endsWith("Z") || original.matches(".*[+-]\\d{2}:\\d{2}$");
            if (!parses(original, zoned)) {
                // shaped like a dateTime but not one (2025-02-30T25:00:00Z): the service leaves it alone
                assertThat(actual).as("unparseable dateTime stays — " + context).isEqualTo(original);
                return;
            }
            assertThat(actual).as("a shifted dateTime is a valid FHIR dateTime (BUG-150) — " + context).matches(FHIR_DATETIME);
            if (zoned) {
                assertThat(OffsetDateTime.parse(actual)).as(context).isEqualTo(OffsetDateTime.parse(original).plus(period));
            } else {
                assertThat(LocalDateTime.parse(actual)).as(context).isEqualTo(LocalDateTime.parse(original).plus(period));
            }
        } else if (original.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            assertThat(LocalDate.parse(actual)).as(context).isEqualTo(LocalDate.parse(original).plus(period));
        } else if (original.matches("^\\d{4}-\\d{2}$")) {
            // only generated for year shifts: a day shift approximates year-months and is not a round trip
            LocalDate expected = LocalDate.parse(original + "-01").plus(period);
            assertThat(actual).as(context).isEqualTo(String.format("%04d-%02d", expected.getYear(), expected.getMonthValue()));
        } else if (original.matches("^\\d{4}$")) {
            int expectedYear = Integer.parseInt(original) + period.getYears();   // a day shift leaves a bare year alone
            assertThat(actual).as(context).isEqualTo(String.format("%04d", expectedYear));
        } else {
            assertThat(actual).as("unrecognised text under a date key stays — " + context).isEqualTo(original);
        }
    }

    private static boolean parses(String text, boolean zoned) {
        try {
            if (zoned) OffsetDateTime.parse(text); else LocalDateTime.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) names.add(it.next());
        return names;
    }
}
