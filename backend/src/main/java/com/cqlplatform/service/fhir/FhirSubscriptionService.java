package com.cqlplatform.service.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.FhirSubscriptionEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.ehr.SubscriptionRequest;
import com.cqlplatform.repository.FhirSubscriptionRepository;
import com.cqlplatform.util.SecurityUtils;
import com.cqlplatform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FhirSubscriptionService {

    private final FhirSubscriptionRepository subscriptionRepository;
    private final EhrConnectionService connectionService;
    private final FhirClientFactory fhirClientFactory;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${fhir.subscription.callback-base-url:}")
    private String callbackBaseUrl;

    @Transactional
    public FhirSubscriptionEntity createSubscription(Long connectionId, SubscriptionRequest request) {
        EhrConnectionEntity connection = connectionService.getById(connectionId);
        IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);

        String callbackUrl = resolveCallbackUrl();

        // Build FHIR Subscription resource
        Subscription subscription = new Subscription();
        subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
        subscription.setCriteria(request.getCriteria());
        subscription.setReason(request.getReason() != null ? request.getReason() : "CQL Platform data sync");

        Subscription.SubscriptionChannelComponent channel = new Subscription.SubscriptionChannelComponent();
        channel.setType(Subscription.SubscriptionChannelType.RESTHOOK);
        channel.setEndpoint(callbackUrl);
        channel.setPayload("application/fhir+json");
        subscription.setChannel(channel);

        // Create on remote FHIR server
        FhirSubscriptionEntity entity = new FhirSubscriptionEntity();
        entity.setConnectionId(connectionId);
        entity.setCriteria(request.getCriteria());
        entity.setCallbackUrl(callbackUrl);
        entity.setReason(request.getReason());
        entity.setCreatedBy(SecurityUtils.getCurrentUsername("system"));

        try {
            var outcome = client.create().resource(subscription).execute();
            String subscriptionId = outcome.getId().getIdPart();
            entity.setFhirSubscriptionId(subscriptionId);
            entity.setStatus("active");
            log.info("Created FHIR Subscription {} on connection {} (criteria: {})",
                    subscriptionId, connectionId, request.getCriteria());
        } catch (Exception e) {
            entity.setStatus("error");
            entity.setErrorMessage(StringUtils.truncate(e.getMessage(), 1000));
            log.warn("Failed to create FHIR Subscription on connection {}: {}",
                    connectionId, e.getMessage());
        }

        return subscriptionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<FhirSubscriptionEntity> listSubscriptions(Long connectionId) {
        if (connectionId != null) {
            return subscriptionRepository.findByConnectionIdOrderByCreatedAtDesc(connectionId);
        }
        return subscriptionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public FhirSubscriptionEntity getSubscription(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
    }

    @Transactional
    public void deleteSubscription(Long id) {
        FhirSubscriptionEntity entity = getSubscription(id);

        // Try to delete from remote FHIR server
        if (entity.getFhirSubscriptionId() != null) {
            try {
                EhrConnectionEntity connection = connectionService.getById(entity.getConnectionId());
                IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);
                client.delete().resourceById("Subscription", entity.getFhirSubscriptionId()).execute();
                log.info("Deleted FHIR Subscription {} from connection {}",
                        entity.getFhirSubscriptionId(), entity.getConnectionId());
            } catch (Exception e) {
                log.warn("Failed to delete remote Subscription {}: {}",
                        entity.getFhirSubscriptionId(), e.getMessage());
            }
        }

        subscriptionRepository.delete(entity);
    }

    @Transactional
    public FhirSubscriptionEntity syncStatus(Long id) {
        FhirSubscriptionEntity entity = getSubscription(id);
        if (entity.getFhirSubscriptionId() == null) {
            return entity;
        }

        try {
            EhrConnectionEntity connection = connectionService.getById(entity.getConnectionId());
            IGenericClient client = fhirClientFactory.createAuthenticatedClient(connection);
            Subscription remote = client.read()
                    .resource(Subscription.class)
                    .withId(entity.getFhirSubscriptionId())
                    .execute();

            entity.setStatus(remote.getStatus() != null ? remote.getStatus().toCode() : "unknown");
            entity.setErrorMessage(remote.hasError() ? remote.getError() : null);
        } catch (Exception e) {
            entity.setStatus("error");
            entity.setErrorMessage("Status sync failed: " + StringUtils.truncate(e.getMessage(), 900));
        }

        return subscriptionRepository.save(entity);
    }

    /**
     * Handle incoming REST-hook notification from a FHIR server.
     */
    @Transactional
    public void handleNotification(String subscriptionId, String resourceJson) {
        FhirSubscriptionEntity entity = subscriptionRepository.findByFhirSubscriptionId(subscriptionId)
                .orElse(null);

        if (entity == null) {
            log.warn("Received notification for unknown subscription: {}", subscriptionId);
            return;
        }

        entity.setLastNotificationAt(LocalDateTime.now());
        entity.setNotificationCount(entity.getNotificationCount() + 1);
        subscriptionRepository.save(entity);

        log.info("Received notification #{} for subscription {} (connection {})",
                entity.getNotificationCount(), subscriptionId, entity.getConnectionId());
    }

    private String resolveCallbackUrl() {
        if (callbackBaseUrl != null && !callbackBaseUrl.isBlank()) {
            return callbackBaseUrl + "/api/ehr/subscriptions/callback";
        }
        return "http://localhost:" + serverPort + "/api/ehr/subscriptions/callback";
    }

}
