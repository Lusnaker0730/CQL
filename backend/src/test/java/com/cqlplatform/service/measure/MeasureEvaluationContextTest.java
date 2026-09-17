package com.cqlplatform.service.measure;

import com.cqlplatform.model.measure.MeasureEvaluationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 (measure path) — the evaluation context must carry the request's connectionId
 * through so doExecutePreTranslated can resolve the authenticated clinic connection.
 */
class MeasureEvaluationContextTest {

    @Test
    void connectionIdAndUrlDelegateToRequest() {
        MeasureEvaluationRequest req = new MeasureEvaluationRequest();
        req.setConnectionId(42L);
        req.setFhirServerUrl("https://clinic.example/fhir");

        MeasureEvaluationContext ctx = MeasureEvaluationContext.builder().request(req).build();

        assertThat(ctx.getConnectionId()).isEqualTo(42L);
        assertThat(ctx.getFhirServerUrl()).isEqualTo("https://clinic.example/fhir");
    }

    @Test
    void connectionIdIsNullWhenUnset() {
        MeasureEvaluationContext ctx = MeasureEvaluationContext.builder()
                .request(new MeasureEvaluationRequest())
                .build();

        assertThat(ctx.getConnectionId()).isNull();
    }
}
