package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsFeedbackEntity;
import com.cqlplatform.model.cds.CdsFeedbackRequest;
import com.cqlplatform.repository.CdsFeedbackRepository;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdsFeedbackTest {

    @Mock
    private CdsServiceConfigRepository repository;
    @Mock
    private CdsFeedbackRepository feedbackRepository;
    @Mock
    private CdsInvocationService invocationService;
    @Mock
    private CqlTupleCardStrategy tupleStrategy;

    private CdsHooksService cdsHooksService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        // PAT-138 — feedbackRepository is now a constructor-injected Optional
        // (no more reflection hack needed). cqlLibraryRepository stays empty
        // since this test doesn't exercise CQL library sync.
        cdsHooksService = new CdsHooksService(repository, objectMapper, invocationService, tupleStrategy,
                java.util.Optional.of(feedbackRepository), java.util.Optional.empty());
    }

    @Test
    void processFeedback_withAccepted_shouldPersist() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc").hook("patient-view").title("Test").build();
        cdsHooksService.registerService(config);

        CdsFeedbackRequest request = CdsFeedbackRequest.builder()
                .feedback(List.of(
                        CdsFeedbackRequest.FeedbackItem.builder()
                                .card("card-uuid-1")
                                .outcome("accepted")
                                .build()
                ))
                .build();

        cdsHooksService.processFeedback("test-svc", request);

        ArgumentCaptor<CdsFeedbackEntity> captor = ArgumentCaptor.forClass(CdsFeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());

        CdsFeedbackEntity saved = captor.getValue();
        assertThat(saved.getServiceId()).isEqualTo("test-svc");
        assertThat(saved.getCardUuid()).isEqualTo("card-uuid-1");
        assertThat(saved.getOutcome()).isEqualTo("accepted");
    }

    @Test
    void processFeedback_withOverride_shouldPersistReason() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc").hook("patient-view").title("Test").build();
        cdsHooksService.registerService(config);

        CdsFeedbackRequest request = CdsFeedbackRequest.builder()
                .feedback(List.of(
                        CdsFeedbackRequest.FeedbackItem.builder()
                                .card("card-uuid-2")
                                .outcome("overridden")
                                .overrideReason(CdsFeedbackRequest.OverrideReason.builder()
                                        .code("clinical-reason")
                                        .display("Patient has contraindication")
                                        .build())
                                .build()
                ))
                .build();

        cdsHooksService.processFeedback("test-svc", request);

        ArgumentCaptor<CdsFeedbackEntity> captor = ArgumentCaptor.forClass(CdsFeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());

        CdsFeedbackEntity saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo("overridden");
        assertThat(saved.getOverrideReasonCode()).isEqualTo("clinical-reason");
        assertThat(saved.getOverrideReasonDisplay()).isEqualTo("Patient has contraindication");
    }

    @Test
    void processFeedback_serviceNotFound_shouldThrow() {
        when(repository.existsById("nonexistent")).thenReturn(false);

        CdsFeedbackRequest request = CdsFeedbackRequest.builder()
                .feedback(List.of(
                        CdsFeedbackRequest.FeedbackItem.builder()
                                .card("card-1").outcome("accepted").build()
                ))
                .build();

        assertThatThrownBy(() -> cdsHooksService.processFeedback("nonexistent", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Service not found");
    }

    @Test
    void processFeedback_emptyFeedback_shouldNotPersist() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc").hook("patient-view").title("Test").build();
        cdsHooksService.registerService(config);

        CdsFeedbackRequest request = CdsFeedbackRequest.builder()
                .feedback(List.of())
                .build();

        cdsHooksService.processFeedback("test-svc", request);

        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void processFeedback_withHtmlInDisplay_shouldEscapeBeforePersist() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc").hook("patient-view").title("Test").build();
        cdsHooksService.registerService(config);

        CdsFeedbackRequest request = CdsFeedbackRequest.builder()
                .feedback(List.of(
                        CdsFeedbackRequest.FeedbackItem.builder()
                                .card("<img src=x>")
                                .outcome("overridden")
                                .overrideReason(CdsFeedbackRequest.OverrideReason.builder()
                                        .code("<b>bold</b>")
                                        .display("<img src=x onerror=alert(1)>")
                                        .build())
                                .build()
                ))
                .build();

        cdsHooksService.processFeedback("test-svc", request);

        ArgumentCaptor<CdsFeedbackEntity> captor = ArgumentCaptor.forClass(CdsFeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());

        CdsFeedbackEntity saved = captor.getValue();
        // All free-text fields must be HTML-escaped
        assertThat(saved.getCardUuid()).isEqualTo("&lt;img src=x&gt;");
        assertThat(saved.getOverrideReasonCode()).isEqualTo("&lt;b&gt;bold&lt;/b&gt;");
        assertThat(saved.getOverrideReasonDisplay()).isEqualTo("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void getFeedback_shouldReturnResults() {
        CdsFeedbackEntity entity = CdsFeedbackEntity.builder()
                .serviceId("test-svc").cardUuid("card-1").outcome("accepted").build();

        when(feedbackRepository.findByServiceIdOrderByCreatedAtDesc("test-svc"))
                .thenReturn(List.of(entity));

        List<CdsFeedbackEntity> result = cdsHooksService.getFeedback("test-svc");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOutcome()).isEqualTo("accepted");
    }
}
