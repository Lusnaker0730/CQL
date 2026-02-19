package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.cds.CdsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CqlTupleCardStrategyTest {

    private CqlTupleCardStrategy strategy;

    @BeforeEach
    void setUp() {
        CdsResourceFormatter resourceFormatter = new CdsResourceFormatter();
        CdsValueFormatter valueFormatter = new CdsValueFormatter(resourceFormatter);
        ObjectMapper objectMapper = new ObjectMapper();
        strategy = new CqlTupleCardStrategy(valueFormatter, objectMapper);
    }

    @Test
    void buildResponse_withBooleanTrue_shouldReturnConsolidatedCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Test Service")
                .defaultIndicator("warning")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("IsEligible", ExpressionResult.builder()
                .name("IsEligible").value(true).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        CdsResponse.Card card = response.getCards().get(0);
        assertThat(card.getSummary()).isEqualTo("Test Service");
        assertThat(card.getDetail()).contains("**IsEligible**: Yes");
        assertThat(card.getIndicator()).isEqualTo("warning");
    }

    @Test
    void buildResponse_withBooleanFalse_shouldReturnNoRecommendationsCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Test Service")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("Check", ExpressionResult.builder()
                .name("Check").value(false).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getDetail()).contains("No recommendations");
    }

    @Test
    void buildResponse_withStringResult_shouldReturnConsolidatedCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Test Service")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("Message", ExpressionResult.builder()
                .name("Message").value("Hello World").valueType("String").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(results)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getDetail()).contains("**Message**: Hello World");
    }

    @Test
    void buildResponse_noResults_shouldReturnNoRecommendationsCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Test Service")
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true)
                .results(null)
                .build();

        CdsResponse response = strategy.buildResponse(config, execResponse);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getDetail()).contains("No recommendations");
    }

    @Test
    void createInfoCard_shouldSetIndicatorToInfo() {
        CdsResponse.Card card = strategy.createInfoCard("Summary", "Detail text");

        assertThat(card.getSummary()).isEqualTo("Summary");
        assertThat(card.getDetail()).isEqualTo("Detail text");
        assertThat(card.getIndicator()).isEqualTo("info");
        assertThat(card.getUuid()).isNotNull();
    }

    @Test
    void createErrorCard_shouldSetIndicatorToWarning() {
        CdsResponse.Card card = strategy.createErrorCard("Something failed");

        assertThat(card.getSummary()).isEqualTo("CDS Service Error");
        assertThat(card.getDetail()).contains("Something failed");
        assertThat(card.getIndicator()).isEqualTo("warning");
    }
}
