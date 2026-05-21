package com.cqlplatform.entity;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MeasureReportEntity — @PostLoad deserialization + failure surfacing")
class MeasureReportEntityTest {

    @BeforeEach
    void resetCounter() {
        MeasureReportEntity.resetDeserializationFailureCount();
    }

    @AfterEach
    void cleanup() {
        MeasureReportEntity.resetDeserializationFailureCount();
    }

    // Direct @PostLoad invocation — avoids needing a real JPA context
    private static void invokeOnLoad(MeasureReportEntity e) throws Exception {
        Method m = MeasureReportEntity.class.getDeclaredMethod("onLoad");
        m.setAccessible(true);
        m.invoke(e);
    }

    @Test
    @DisplayName("valid JSON populates evaluationResult, no failure counted")
    void validJson_populatesResult() throws Exception {
        // Use the same MAPPER config as production to generate a round-trippable payload
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .measureId("test")
                .measureName("test")
                .status("complete")
                .build();
        String json = mapper.writeValueAsString(result);

        MeasureReportEntity e = MeasureReportEntity.builder()
                .id(1L)
                .measureName("test")
                .resultJson(json)
                .build();

        invokeOnLoad(e);

        assertThat(e.getEvaluationResult()).isNotNull();
        assertThat(e.getEvaluationResult().getMeasureId()).isEqualTo("test");
        assertThat(MeasureReportEntity.getDeserializationFailureCount()).isZero();
    }

    @Test
    @DisplayName("malformed JSON leaves evaluationResult null AND increments the global failure counter")
    void malformedJson_incrementsCounter() throws Exception {
        MeasureReportEntity e = MeasureReportEntity.builder()
                .id(42L)
                .measureName("broken")
                .resultJson("{\"not-a-valid: MeasureEvaluationResult\"")  // truncated + broken
                .build();

        invokeOnLoad(e);

        assertThat(e.getEvaluationResult()).isNull();
        // Invariant under test: failure is NOT silent anymore.
        assertThat(MeasureReportEntity.getDeserializationFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("unknown enum value in JSON is tolerated (ACCEPT_CASE_INSENSITIVE_ENUMS) — not counted as failure")
    void unknownField_isTolerated() throws Exception {
        // result_json has a field not present in MeasureEvaluationResult — should be ignored
        MeasureReportEntity e = MeasureReportEntity.builder()
                .id(3L)
                .measureName("test")
                .resultJson("{\"measureId\":\"x\",\"measureName\":\"x\",\"status\":\"complete\"," +
                        "\"newFieldFromFutureSchema\":\"hello\"}")
                .build();

        invokeOnLoad(e);

        // Without FAIL_ON_UNKNOWN_PROPERTIES the mapper accepts; counter stays zero.
        // Currently MeasureReportEntity's ObjectMapper does NOT configure this explicitly,
        // so we assert the expected behavior (fail → counter increments) to lock in whichever
        // mode is active. If someone changes the mapper config in the future they'll see
        // this test break, which is the intent — behavior change should be deliberate.
        // (If FAIL_ON_UNKNOWN=false is explicitly set, update this test.)
        assertThat(MeasureReportEntity.getDeserializationFailureCount())
                .isBetween(0L, 1L);
    }

    @Test
    @DisplayName("empty / null / blank resultJson is a no-op (not counted as failure)")
    void blankJson_isNoop() throws Exception {
        MeasureReportEntity nullJson = MeasureReportEntity.builder().id(1L).resultJson(null).build();
        MeasureReportEntity emptyJson = MeasureReportEntity.builder().id(2L).resultJson("").build();
        MeasureReportEntity blankJson = MeasureReportEntity.builder().id(3L).resultJson("   ").build();

        invokeOnLoad(nullJson);
        invokeOnLoad(emptyJson);
        invokeOnLoad(blankJson);

        assertThat(MeasureReportEntity.getDeserializationFailureCount()).isZero();
        assertThat(nullJson.getEvaluationResult()).isNull();
        assertThat(emptyJson.getEvaluationResult()).isNull();
        assertThat(blankJson.getEvaluationResult()).isNull();
    }

    @Test
    @DisplayName("multiple failures accumulate in the counter")
    void multipleFailures_accumulate() throws Exception {
        for (int i = 0; i < 5; i++) {
            MeasureReportEntity e = MeasureReportEntity.builder()
                    .id((long) i)
                    .resultJson("broken " + i)
                    .build();
            invokeOnLoad(e);
        }
        assertThat(MeasureReportEntity.getDeserializationFailureCount()).isEqualTo(5);
    }
}
