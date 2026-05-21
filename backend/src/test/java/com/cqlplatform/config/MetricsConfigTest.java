package com.cqlplatform.config;

import com.cqlplatform.entity.MeasureReportEntity;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetricsConfig — measure_report_deserialization_failures gauge")
class MetricsConfigTest {

    private final MetricsConfig metricsConfig = new MetricsConfig();
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MeasureReportEntity.resetDeserializationFailureCount();
    }

    @AfterEach
    void tearDown() {
        MeasureReportEntity.resetDeserializationFailureCount();
    }

    @Test
    @DisplayName("gauge registers with the expected name and reads the live counter")
    void gaugeTracksLiveCounter() throws Exception {
        Gauge gauge = metricsConfig.measureReportDeserializationFailures(registry);

        assertThat(gauge).isNotNull();
        assertThat(registry.find("measure.report.deserialization.failures").gauge())
                .as("gauge must be discoverable via registry lookup under the documented name")
                .isNotNull();
        assertThat(gauge.value()).isZero();

        // Trigger a deserialization failure via the entity's @PostLoad path; the gauge
        // reads the same LongAdder so its value must advance in lockstep.
        MeasureReportEntity broken = MeasureReportEntity.builder()
                .id(1L)
                .resultJson("{broken")
                .build();
        Method onLoad = MeasureReportEntity.class.getDeclaredMethod("onLoad");
        onLoad.setAccessible(true);
        onLoad.invoke(broken);

        assertThat(gauge.value())
                .as("gauge must reflect the failure counter without a restart — alerting relies on this")
                .isEqualTo(1.0);
    }
}
