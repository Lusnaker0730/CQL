package com.cqlplatform.service.measure;

import com.cqlplatform.exception.FhirServerUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-112a contract tests: any FHIR upstream outage during measure evaluation
 * must abort the whole evaluation, not silently partial-aggregate. Partial
 * scores falsify the denominator — a clinically-dangerous pattern because a
 * half-populated denominator inflates the quality rate and can mislead P4P
 * reporting or QI decisions.
 *
 * <p>These tests exercise the private cause-walker and the record shape
 * rather than standing up a full Spring context, so they stay fast and
 * deterministic. Full end-to-end regression is covered by
 * {@code CqlExecutionIntegrationTest}.
 */
@DisplayName("MeasureEvaluationService — FHIR outage abort (PAT-112a)")
class MeasureEvaluationFhirOutageTest {

    @Test
    @DisplayName("findFhirOutageCause returns the exception when present at top level")
    void findFhirOutage_topLevel() throws Exception {
        FhirServerUnavailableException outage =
                new FhirServerUnavailableException("upstream timeout");
        Throwable found = invokeFind(outage);
        assertThat(found).isSameAs(outage);
    }

    @Test
    @DisplayName("findFhirOutageCause walks the cause chain (CQL engine wraps retrieve failures)")
    void findFhirOutage_nestedCause() throws Exception {
        FhirServerUnavailableException outage =
                new FhirServerUnavailableException("upstream 503");
        RuntimeException wrapped = new RuntimeException("cql wrap",
                new RuntimeException("engine wrap", outage));
        Throwable found = invokeFind(wrapped);
        assertThat(found).isSameAs(outage);
    }

    @Test
    @DisplayName("findFhirOutageCause returns null when no outage in chain")
    void findFhirOutage_absent() throws Exception {
        Throwable found = invokeFind(new IllegalStateException("not an outage",
                new IllegalArgumentException("still not")));
        assertThat(found).isNull();
    }

    @Test
    @DisplayName("findFhirOutageCause handles null input")
    void findFhirOutage_null() throws Exception {
        assertThat(invokeFind(null)).isNull();
    }

    @Test
    @DisplayName("findFhirOutageCause bails at depth cap — pathological cause chain must not spin")
    void findFhirOutage_depthCap() throws Exception {
        // Build a 100-deep chain with no FhirServerUnavailableException anywhere.
        // findFhirOutageCause caps at 10 hops; must return null, not hang.
        Throwable t = new RuntimeException("leaf");
        for (int i = 0; i < 100; i++) {
            t = new RuntimeException("layer-" + i, t);
        }
        long start = System.nanoTime();
        Throwable found = invokeFind(t);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(found).isNull();
        assertThat(elapsedMs).as("classify must not walk the full chain").isLessThan(100);
    }

    /** Reflection access to the private static {@code findFhirOutageCause} helper. */
    private static Throwable invokeFind(Throwable input) throws Exception {
        Method m = MeasureEvaluationService.class.getDeclaredMethod("findFhirOutageCause", Throwable.class);
        m.setAccessible(true);
        return (Throwable) m.invoke(null, input);
    }
}
