package com.cqlplatform.service;

import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAT-167: SSE removed. Real-time push now flows through
 * {@link NotificationWebSocketHandler} instead. The CRUD + workflow API
 * surface (create, mark read, delete, notify*) is unchanged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationWebSocketHandler webSocketHandler;
    private final com.cqlplatform.repository.TenantRepository tenantRepository;

    /** Caller's tenant ?? default — see EhrConnectionService for the canonical pattern. */
    private Long effectiveTenantId() {
        Long tenantId = com.cqlplatform.security.TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return tenantRepository.findByCode("default")
                .map(com.cqlplatform.entity.TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    // ===== CRUD =====

    @Transactional(readOnly = true)
    public List<NotificationEntity> getNotifications(String recipient) {
        return repository.findTop50ByTenantIdAndRecipientOrderByCreatedAtDesc(effectiveTenantId(), recipient);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipient) {
        return repository.countByTenantIdAndRecipientAndReadFalse(effectiveTenantId(), recipient);
    }

    @Transactional
    public NotificationEntity markAsRead(Long id, String recipient) {
        NotificationEntity notification = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        if (!notification.getRecipient().equals(recipient)) {
            throw new IllegalArgumentException("Not your notification");
        }
        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());
        return repository.save(notification);
    }

    @Transactional
    public int markAllAsRead(String recipient) {
        return repository.markAllAsRead(effectiveTenantId(), recipient);
    }

    @Transactional
    public void deleteNotification(Long id, String recipient) {
        NotificationEntity notification = repository.findByIdAndTenantId(id, effectiveTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        if (!notification.getRecipient().equals(recipient)) {
            throw new IllegalArgumentException("Not your notification");
        }
        repository.delete(notification);
    }

    // ===== Create & Push =====

    @Transactional
    public NotificationEntity createNotification(String recipient, String type, String title, String message, String link) {
        NotificationEntity notification = NotificationEntity.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .read(false)
                // Workflow notifications are intra-tenant: the actor and the recipients
                // (reviewers/owner) belong to the same clinic, so the actor's tenant applies.
                .tenantId(effectiveTenantId())
                .build();
        notification = repository.save(notification);
        webSocketHandler.pushToUser(recipient, notification);
        return notification;
    }

    // ===== Workflow Notifications =====

    public void notifyMeasureSubmitted(String ownerUser, List<String> reviewers, String measureName, Long measureId) {
        for (String reviewer : reviewers) {
            if (!reviewer.equals(ownerUser)) {
                createNotification(reviewer, "MEASURE_SUBMITTED",
                        "Measure submitted for review: " + measureName,
                        ownerUser + " submitted \"" + measureName + "\" for your review.",
                        "/measures/" + measureId);
            }
        }
    }

    public void notifyMeasureApproved(String approver, String owner, String measureName, Long measureId) {
        if (!approver.equals(owner)) {
            createNotification(owner, "MEASURE_APPROVED",
                    "Measure approved: " + measureName,
                    approver + " approved \"" + measureName + "\".",
                    "/measures/" + measureId);
        }
    }

    public void notifyMeasureRejected(String reviewer, String owner, String measureName, Long measureId, String reason) {
        if (!reviewer.equals(owner)) {
            createNotification(owner, "MEASURE_REJECTED",
                    "Measure rejected: " + measureName,
                    reviewer + " rejected \"" + measureName + "\""
                            + (reason != null && !reason.isBlank() ? ": " + reason : "."),
                    "/measures/" + measureId);
        }
    }

    public void notifyMeasureShared(String sharer, String targetUser, String measureName, Long measureId) {
        if (!sharer.equals(targetUser)) {
            createNotification(targetUser, "MEASURE_SHARED",
                    "Measure shared with you: " + measureName,
                    sharer + " shared \"" + measureName + "\" with you.",
                    "/measures/" + measureId);
        }
    }
}
