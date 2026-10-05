package com.cqlplatform.service.measure;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateShiftServiceTest {

    private final DateShiftService service = new DateShiftService();

    @Test
    void shiftDates_shouldShiftFhirDateFields() {
        String bundle = """
                {"resourceType":"Bundle","entry":[{"resource":{"resourceType":"Patient","birthDate":"2000-01-15"}}]}""";

        String result = service.shiftDates(bundle, 30);

        assertThat(result).contains("2000-02-14");
    }

    @Test
    void shiftDates_zeroDays_shouldReturnOriginal() {
        String bundle = """
                {"resourceType":"Bundle","entry":[{"resource":{"resourceType":"Patient","birthDate":"2000-01-15"}}]}""";

        String result = service.shiftDates(bundle, 0);

        assertThat(result).isEqualTo(bundle);
    }

    @Test
    void shiftDates_nullInput_shouldReturnNull() {
        assertThat(service.shiftDates(null, 30)).isNull();
    }

    @Test
    void calculateAutoShift_shouldReturnDaysDifference() {
        String bundle = """
                {"resourceType":"Bundle","entry":[{"resource":{"resourceType":"Observation","effectiveDateTime":"2024-01-15T10:30:00Z"}}]}""";

        int shift = service.calculateAutoShift(bundle, LocalDate.of(2024, 6, 15));

        // From 2024-01-15 to 2024-06-15 = 152 days
        assertThat(shift).isEqualTo(152);
    }
    // ===== PAT-248: whole-year shifts =====

    @Test
    void shiftYears_movesEveryDateKind_byWholeYears_keepingMonthAndDay() {
        String bundle = """
                {"resourceType":"Bundle","entry":[
                  {"resource":{"resourceType":"Patient","birthDate":"1960-05-01","deceasedDateTime":"2024-02-29T10:00:00+08:00"}},
                  {"resource":{"resourceType":"Encounter","period":{"start":"2024-03-10T08:00:00Z","end":"2024-03-12T08:00:00Z"}}},
                  {"resource":{"resourceType":"Observation","effectiveDateTime":"2024-07-01T09:30:00","issued":"2024-07-01T09:35:00Z","valueQuantity":{"value":6.8}}},
                  {"resource":{"resourceType":"Condition","onsetDateTime":"2023-11","recordedDate":"2023","code":{"text":"start of care 2023"}}}
                ]}""";

        String result = service.shiftYears(bundle, 1);

        assertThat(result).contains("\"birthDate\":\"1961-05-01\"");
        assertThat(result).as("29 Feb lands on 28 Feb in a non-leap year").contains("\"deceasedDateTime\":\"2025-02-28T10:00+08:00\"");
        assertThat(result).contains("\"start\":\"2025-03-10T08:00Z\"").contains("\"end\":\"2025-03-12T08:00Z\"");
        assertThat(result).contains("\"effectiveDateTime\":\"2025-07-01T09:30\"").contains("\"issued\":\"2025-07-01T09:35Z\"");
        assertThat(result).as("YYYY-MM keeps its precision").contains("\"onsetDateTime\":\"2024-11\"");
        assertThat(result).as("a bare year moves too (a day shift leaves it alone)").contains("\"recordedDate\":\"2024\"");
        assertThat(result).as("non-date fields are untouched").contains("\"value\":6.8").contains("start of care 2023");
    }

    @Test
    void shiftYears_backwards_andBackAgain_isTheIdentity() {
        String bundle = """
                {"resourceType":"Bundle","entry":[{"resource":{"resourceType":"Encounter","period":{"start":"2024-02-01T08:00:00Z","end":"2024-02-03T08:00:00Z"}}}]}""";

        String back = service.shiftYears(bundle, -1);
        assertThat(back).contains("2023-02-01T08:00Z").contains("2023-02-03T08:00Z");
        assertThat(service.shiftYears(back, 1)).contains("2024-02-01T08:00Z").contains("2024-02-03T08:00Z");
        assertThat(service.shiftYears(bundle, 0)).isSameAs(bundle);
    }

    @Test
    void shiftYears_rejectsABundleThatIsNotJson_whereTheDayShiftStaysSilent() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.shiftYears("{not json", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
        assertThat(service.shiftDates("{not json", 1)).isEqualTo("{not json");
        assertThat(service.shiftDates("{\"resourceType\":\"Patient\",\"birthDate\":\"2000\"}", 30))
                .as("a day shift still leaves a bare year alone").contains("\"birthDate\":\"2000\"");
    }
}
