package com.cqlplatform.service;

import com.cqlplatform.config.WebSocketConfig;
import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PAT-167: WebSocket replacement for the previous SSE notification stream.
 *
 * <p>Why WebSocket: the old SSE keep-alive (application-layer comment lines)
 * was insufficient to keep connections alive past Cloudflare's 100s origin
 * idle timeout, even with a 25s cadence — Cloudflare's idle detector does
 * not reliably count {@code :keepalive\n\n} frames as activity. WebSocket
 * has protocol-level ping/pong frames that Cloudflare understands natively,
 * and its idle window for WS is 300s (3x SSE) AND ping always counts as
 * activity. This change eliminates the entire {@code ERR_QUIC_PROTOCOL_ERROR
 * / 524 Origin Timeout} family of issues.
 *
 * <p>The handler keeps an authenticated user → sessions map (a user may have
 * multiple tabs open; each gets its own session). On message creation,
 * {@link NotificationService} delegates push to {@link #pushToUser(String, NotificationEntity)}
 * which serializes the notification to JSON and broadcasts to all of that
 * user's sessions.
 */
@Component
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    private final Map<String, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(NotificationRepository repository,
                                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String username = (String) session.getAttributes().get(WebSocketConfig.USERNAME_ATTR);
        if (username == null) {
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing username attribute"));
            } catch (IOException ignored) {
                // Already closing — the client will observe a transport error.
            }
            return;
        }
        sessions.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(session);

        // Mirror the SSE behaviour: send the initial unread count so the UI badge
        // is correct on first paint without waiting for a notification event.
        long unread = repository.countByRecipientAndReadFalse(username);
        sendJson(session, Map.of("type", "unread-count", "count", unread));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = (String) session.getAttributes().get(WebSocketConfig.USERNAME_ATTR);
        if (username == null) return;
        List<WebSocketSession> userSessions = sessions.get(username);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(username);
            }
        }
    }

    /**
     * Called by {@link NotificationService#createNotification(String, String, String, String, String)}.
     */
    public void pushToUser(String username, NotificationEntity notification) {
        List<WebSocketSession> userSessions = sessions.get(username);
        if (userSessions == null || userSessions.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "type", "notification",
                "id", notification.getId(),
                "notificationType", notification.getType(),
                "title", notification.getTitle(),
                "message", notification.getMessage() != null ? notification.getMessage() : "",
                "link", notification.getLink() != null ? notification.getLink() : "",
                "createdAt", notification.getCreatedAt().toString()
        );

        for (WebSocketSession session : userSessions) {
            sendJson(session, payload);
        }
    }

    /**
     * Sends a protocol-level WebSocket ping to every open session every 25s.
     * Cloudflare counts ping frames as activity, so the origin idle timer
     * never expires; the browser auto-replies with pong. The 25s cadence
     * (well below Cloudflare's 300s WS idle window) is intentional belt-and-
     * suspenders — even if a PoP runs a stricter timeout, ping easily clears.
     */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void sendKeepalivePings() {
        if (sessions.isEmpty()) return;
        ByteBuffer emptyPayload = ByteBuffer.allocate(0);
        for (List<WebSocketSession> userSessions : sessions.values()) {
            for (WebSocketSession session : userSessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new PingMessage(emptyPayload));
                    }
                } catch (IOException e) {
                    // Connection already gone — afterConnectionClosed cleanup
                    // will fire as soon as the transport layer reports it.
                }
            }
        }
    }

    /**
     * Test-visible session count for a user (no synchronization beyond what
     * {@link CopyOnWriteArrayList} already provides — this is a snapshot).
     */
    int sessionCount(String username) {
        List<WebSocketSession> userSessions = sessions.get(username);
        return userSessions == null ? 0 : userSessions.size();
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.debug("WebSocket send failed for session {}: {}", session.getId(), e.getMessage());
        }
    }
}
