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

    // ===== PAT-262 review notifications =====

    public void notifyReviewAssigned(String assigner, String reviewer, String measureName, Long measureId) {
        if (reviewer == null || reviewer.equals(assigner)) return;
        createNotification(reviewer, "MEASURE_REVIEW_ASSIGNED",
                "You were asked to review: " + measureName,
                assigner + " assigned you as a reviewer of \"" + measureName + "\".",
                "/measures?measure=" + measureId);
    }

    public void notifyReviewDecision(String reviewer, String owner, String measureName, Long measureId, String decision, String comment) {
        if (!hasRecipient(owner, "MEASURE_REVIEW_DECISION", measureId) || reviewer.equals(owner)) return;
        createNotification(owner, "MEASURE_REVIEW_DECISION",
                "Review decision on " + measureName + ": " + decision,
                reviewer + " recorded \"" + decision + "\" on \"" + measureName + "\""
                        + (comment != null && !comment.isBlank() ? ": " + comment : "."),
                "/measures?measure=" + measureId);
    }

    /** One notification per recipient (the author is never among them). */
    public void notifyReviewComment(String author, List<String> recipients, String measureName, Long measureId, String body) {
        if (recipients == null) return;
        String excerpt = body == null ? "" : (body.length() > 140 ? body.substring(0, 140) + "…" : body);
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank() || recipient.equals(author)) continue;
            createNotification(recipient, "MEASURE_COMMENT",
                    "New review comment on " + measureName,
                    author + ": " + excerpt,
                    "/measures?measure=" + measureId);
        }
    }

    public void notifyMeasureApproved(String approver, String owner, String measureName, Long measureId) {
        if (!hasRecipient(owner, "MEASURE_APPROVED", measureId)) {
            return;
        }
        if (!approver.equals(owner)) {
            createNotification(owner, "MEASURE_APPROVED",
                    "Measure approved: " + measureName,
                    approver + " approved \"" + measureName + "\".",
                    "/measures/" + measureId);
        }
    }

    public void notifyMeasureRejected(String reviewer, String owner, String measureName, Long measureId, String reason) {
        if (!hasRecipient(owner, "MEASURE_REJECTED", measureId)) {
            return;
        }
        if (!reviewer.equals(owner)) {
            createNotification(owner, "MEASURE_REJECTED",
                    "Measure rejected: " + measureName,
                    reviewer + " rejected \"" + measureName + "\""
                            + (reason != null && !reason.isBlank() ? ": " + reason : "."),
                    "/measures/" + measureId);
        }
    }

    /**
     * BUG-143: measures created through the UI (and by any API client that omits
     * {@code ownerUsername}) have no owner. Building a notification with a null
     * recipient used to hit {@code notification.recipient NOT NULL}, roll back the whole
     * approve / reject transaction and surface as a misleading 409 "database constraint"
     * error — the review workflow could never complete for such measures. No recipient
     * simply means there is nobody to tell.
     */
    private boolean hasRecipient(String recipient, String type, Long measureId) {
        if (recipient == null || recipient.isBlank()) {
            log.info("Skipping {} notification for measure {}: measure has no owner to notify", type, measureId);
            return false;
        }
        return true;
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
