package com.cqlplatform.service;

import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.repository.NotificationRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BUG-143 — workflow notifications for ownerless measures. Measures created through the
 * UI carry no {@code ownerUsername}; approve / reject used to try to notify a null
 * recipient, hit the NOT NULL constraint and roll back the whole workflow transaction.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationWebSocketHandler webSocketHandler;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private NotificationService service;

    @BeforeEach
    void setTenant() {
        // A concrete tenant on the thread keeps effectiveTenantId() away from the repository.
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void notifyMeasureApproved_ownerlessMeasure_skipsInsteadOfInsertingNullRecipient(String owner) {
        assertThatCode(() -> service.notifyMeasureApproved("reviewer", owner, "Smoke measure", 4L))
                .doesNotThrowAnyException();

        verifyNoInteractions(repository, webSocketHandler);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void notifyMeasureRejected_ownerlessMeasure_skipsInsteadOfInsertingNullRecipient(String owner) {
        assertThatCode(() -> service.notifyMeasureRejected("reviewer", owner, "Smoke measure", 4L, "needs work"))
                .doesNotThrowAnyException();

        verifyNoInteractions(repository, webSocketHandler);
    }

    @Test
    void notifyMeasureApproved_withOwner_stillNotifiesTheOwner() {
        when(repository.save(any(NotificationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyMeasureApproved("reviewer", "alice", "Smoke measure", 4L);

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isEqualTo("alice");
        assertThat(saved.getValue().getType()).isEqualTo("MEASURE_APPROVED");
        assertThat(saved.getValue().getLink()).isEqualTo("/measures/4");
        assertThat(saved.getValue().getTenantId()).isEqualTo(1L);
        verify(webSocketHandler).pushToUser(eq("alice"), any(NotificationEntity.class));
    }

    @Test
    void notifyMeasureApproved_approverIsOwner_doesNotSelfNotify() {
        service.notifyMeasureApproved("alice", "alice", "Smoke measure", 4L);

        verifyNoInteractions(repository, webSocketHandler);
    }
}
