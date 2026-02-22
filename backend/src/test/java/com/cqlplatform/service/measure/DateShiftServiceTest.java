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
}
