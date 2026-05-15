package com.cqlplatform.service;

import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    // SSE emitters per user for real-time push
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ===== CRUD =====

    @Transactional(readOnly = true)
    public List<NotificationEntity> getNotifications(String recipient) {
        return repository.findTop50ByRecipientOrderByCreatedAtDesc(recipient);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipient) {
        return repository.countByRecipientAndReadFalse(recipient);
    }

    @Transactional
    public NotificationEntity markAsRead(Long id, String recipient) {
        NotificationEntity notification = repository.findById(id)
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
        return repository.markAllAsRead(recipient);
    }

    @Transactional
    public void deleteNotification(Long id, String recipient) {
        NotificationEntity notification = repository.findById(id)
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
                .build();
        notification = repository.save(notification);
        pushToUser(recipient, notification);
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

    // ===== SSE =====

    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(username, emitter));
        emitter.onTimeout(() -> removeEmitter(username, emitter));
        emitter.onError(e -> removeEmitter(username, emitter));

        // Send initial unread count
        try {
            long unread = getUnreadCount(username);
            emitter.send(SseEmitter.event().name("unread-count").data(unread));
        } catch (IOException e) {
            removeEmitter(username, emitter);
        }

        return emitter;
    }

    /**
     * PAT-165 follow-up: keep SSE connections alive across Cloudflare's
     * cloudflared tunnel timeout (~100s idle) and Chrome's QUIC stack idle
     * detection. Without periodic traffic, the connection silently drops
     * mid-flight and the browser logs ERR_QUIC_PROTOCOL_ERROR. SSE comments
     * (lines starting with {@code :}) are valid per the spec and are
     * delivered as keep-alive bytes that don't fire any client-side event.
     *
     * <p>25s cadence stays well under the 100s edge timeout with margin for
     * jitter.
     */
    @Scheduled(fixedRate = 25_000)
    public void sendSseKeepalives() {
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    // Client gone — let the next removeEmitter call (via
                    // onError/onCompletion) clean up. Don't try to remove
                    // here to avoid concurrent modification on the list.
                }
            }
        }
    }

    private void pushToUser(String username, NotificationEntity notification) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(Map.of(
                                "id", notification.getId(),
                                "type", notification.getType(),
                                "title", notification.getTitle(),
                                "message", notification.getMessage() != null ? notification.getMessage() : "",
                                "link", notification.getLink() != null ? notification.getLink() : "",
                                "createdAt", notification.getCreatedAt().toString()
                        )));
            } catch (IOException e) {
                removeEmitter(username, emitter);
            }
        }
    }

    private void removeEmitter(String username, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
        }
    }
}
