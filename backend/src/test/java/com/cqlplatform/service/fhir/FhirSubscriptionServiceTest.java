package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreate;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.IDelete;
import ca.uhn.fhir.rest.gclient.IDeleteTyped;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.FhirSubscriptionEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.ehr.SubscriptionRequest;
import com.cqlplatform.repository.FhirSubscriptionRepository;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Subscription;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirSubscriptionServiceTest {

    @Mock
    private FhirSubscriptionRepository subscriptionRepository;

    @Mock
    private EhrConnectionService connectionService;

    @Mock
    private FhirClientFactory fhirClientFactory;

    @InjectMocks
    private FhirSubscriptionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "serverPort", 8080);
        ReflectionTestUtils.setField(service, "callbackBaseUrl", "http://test:8080");
    }

    @Test
    void createSubscription_success_shouldSetActiveStatus() {
        Long connectionId = 1L;
        SubscriptionRequest request = new SubscriptionRequest();
        request.setCriteria("Patient?_lastUpdated=gt2024-01-01");
        request.setReason("Test sync");

        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setId(connectionId);
        when(connectionService.getById(connectionId)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        ICreate create = mock(ICreate.class);
        ICreateTyped createTyped = mock(ICreateTyped.class);
        when(client.create()).thenReturn(create);
        when(create.resource(any(Subscription.class))).thenReturn(createTyped);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType("Subscription", "sub-123"));
        when(createTyped.execute()).thenReturn(outcome);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FhirSubscriptionEntity result = service.createSubscription(connectionId, request);

        assertThat(result.getStatus()).isEqualTo("active");
        assertThat(result.getFhirSubscriptionId()).isEqualTo("sub-123");
        assertThat(result.getCriteria()).isEqualTo("Patient?_lastUpdated=gt2024-01-01");
        assertThat(result.getCallbackUrl()).isEqualTo("http://test:8080/api/ehr/subscriptions/callback");
    }

    @Test
    void createSubscription_failure_shouldSetErrorStatus() {
        Long connectionId = 1L;
        SubscriptionRequest request = new SubscriptionRequest();
        request.setCriteria("Patient?_lastUpdated=gt2024-01-01");

        EhrConnectionEntity connection = new EhrConnectionEntity();
        connection.setId(connectionId);
        when(connectionService.getById(connectionId)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        ICreate create = mock(ICreate.class);
        ICreateTyped createTyped = mock(ICreateTyped.class);
        when(client.create()).thenReturn(create);
        when(create.resource(any(Subscription.class))).thenReturn(createTyped);
        when(createTyped.execute()).thenThrow(new RuntimeException("Connection refused"));

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FhirSubscriptionEntity result = service.createSubscription(connectionId, request);

        assertThat(result.getStatus()).isEqualTo("error");
        assertThat(result.getErrorMessage()).contains("Connection refused");
        assertThat(result.getFhirSubscriptionId()).isNull();
    }

    @Test
    void listSubscriptions_withConnectionId_shouldFilter() {
        Long connectionId = 1L;
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setConnectionId(connectionId);
        when(subscriptionRepository.findByConnectionIdOrderByCreatedAtDesc(connectionId))
                .thenReturn(List.of(entity));

        List<FhirSubscriptionEntity> result = service.listSubscriptions(connectionId);

        assertThat(result).hasSize(1);
        verify(subscriptionRepository).findByConnectionIdOrderByCreatedAtDesc(connectionId);
        verify(subscriptionRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void listSubscriptions_withoutConnectionId_shouldReturnAll() {
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        when(subscriptionRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(entity));

        List<FhirSubscriptionEntity> result = service.listSubscriptions(null);

        assertThat(result).hasSize(1);
        verify(subscriptionRepository).findAllByOrderByCreatedAtDesc();
        verify(subscriptionRepository, never()).findByConnectionIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getSubscription_found_shouldReturn() {
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setId(1L);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(entity));

        FhirSubscriptionEntity result = service.getSubscription(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getSubscription_notFound_shouldThrow() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteSubscription_shouldDeleteLocalAndRemote() {
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setId(1L);
        entity.setConnectionId(10L);
        entity.setFhirSubscriptionId("sub-123");
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(entity));

        EhrConnectionEntity connection = new EhrConnectionEntity();
        when(connectionService.getById(10L)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        IDelete delete = mock(IDelete.class);
        IDeleteTyped deleteTyped = mock(IDeleteTyped.class);
        when(client.delete()).thenReturn(delete);
        when(delete.resourceById("Subscription", "sub-123")).thenReturn(deleteTyped);

        service.deleteSubscription(1L);

        verify(deleteTyped).execute();
        verify(subscriptionRepository).delete(entity);
    }

    @Test
    void handleNotification_knownSubscription_shouldIncrementCount() {
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setFhirSubscriptionId("sub-123");
        entity.setNotificationCount(5);
        entity.setConnectionId(1L);
        when(subscriptionRepository.findByFhirSubscriptionId("sub-123"))
                .thenReturn(Optional.of(entity));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleNotification("sub-123", "{\"resourceType\":\"Patient\"}");

        assertThat(entity.getNotificationCount()).isEqualTo(6);
        assertThat(entity.getLastNotificationAt()).isNotNull();
        verify(subscriptionRepository).save(entity);
    }

    @Test
    void handleNotification_unknownSubscription_shouldLogWarning() {
        when(subscriptionRepository.findByFhirSubscriptionId("unknown"))
                .thenReturn(Optional.empty());

        service.handleNotification("unknown", "{}");

        verify(subscriptionRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncStatus_shouldUpdateFromRemote() {
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setId(1L);
        entity.setConnectionId(10L);
        entity.setFhirSubscriptionId("sub-123");
        entity.setStatus("requested");
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(entity));

        EhrConnectionEntity connection = new EhrConnectionEntity();
        when(connectionService.getById(10L)).thenReturn(connection);

        IGenericClient client = mock(IGenericClient.class);
        when(fhirClientFactory.createAuthenticatedClient(connection)).thenReturn(client);

        Subscription remote = new Subscription();
        remote.setStatus(Subscription.SubscriptionStatus.ACTIVE);

        IRead read = mock(IRead.class);
        IReadTyped<Subscription> readTyped = mock(IReadTyped.class);
        IReadExecutable<Subscription> readExecutable = mock(IReadExecutable.class);
        when(client.read()).thenReturn(read);
        when(read.resource(Subscription.class)).thenReturn(readTyped);
        when(readTyped.withId("sub-123")).thenReturn(readExecutable);
        when(readExecutable.execute()).thenReturn(remote);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FhirSubscriptionEntity result = service.syncStatus(1L);

        assertThat(result.getStatus()).isEqualTo("active");
    }
}
