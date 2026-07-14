package com.cqlplatform.service;

import com.cqlplatform.config.WebSocketConfig;
import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAT-167: unit tests for the WebSocket replacement of SSE notifications.
 * Locks the behaviour that the conversation log of 2026-05-20/2026-05-21
 * exercised end-to-end against production (Cloudflare 524 / QUIC drops),
 * plus the new connection-lifecycle invariants that did not exist before.
 */
class NotificationWebSocketHandlerTest {

    private NotificationRepository repository;
    private NotificationWebSocketHandler handler;
    private com.cqlplatform.repository.UserRepository userRepository;
    private com.cqlplatform.repository.TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        userRepository = org.mockito.Mockito.mock(com.cqlplatform.repository.UserRepository.class);
        tenantRepository = org.mockito.Mockito.mock(com.cqlplatform.repository.TenantRepository.class);
        org.mockito.Mockito.lenient().when(tenantRepository.findByCode("default")).thenReturn(
                java.util.Optional.of(com.cqlplatform.entity.TenantEntity.builder().id(1L).code("default").build()));
        handler = new NotificationWebSocketHandler(repository, userRepository, tenantRepository, new ObjectMapper());
    }

    private WebSocketSession session(String username) throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        if (username != null) attrs.put(WebSocketConfig.USERNAME_ATTR, username);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void afterConnectionEstablished_registersSessionAndSendsInitialUnreadCount() throws Exception {
        when(repository.countByTenantIdAndRecipientAndReadFalse(1L, "alice")).thenReturn(3L);
        WebSocketSession s = session("alice");

        handler.afterConnectionEstablished(s);

        assertThat(handler.sessionCount("alice")).isEqualTo(1);
        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(s).sendMessage(sent.capture());
        TextMessage tm = (TextMessage) sent.getValue();
        assertThat(tm.getPayload()).contains("\"type\":\"unread-count\"");
        assertThat(tm.getPayload()).contains("\"count\":3");
    }

    @Test
    void afterConnectionEstablished_closesSessionIfUsernameAttrMissing() throws Exception {
        // Missing username attribute means the handshake interceptor didn't set
        // it — auth invariant broken; refuse to register and close the socket.
        WebSocketSession s = session(null);
        handler.afterConnectionEstablished(s);

        assertThat(handler.sessionCount("alice")).isZero();
        verify(s).close(any(CloseStatus.class));
    }

    @Test
    void afterConnectionClosed_removesSession() throws Exception {
        when(repository.countByTenantIdAndRecipientAndReadFalse(1L, "alice")).thenReturn(0L);
        WebSocketSession s = session("alice");
        handler.afterConnectionEstablished(s);
        assertThat(handler.sessionCount("alice")).isEqualTo(1);

        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        assertThat(handler.sessionCount("alice")).isZero();
    }

    @Test
    void pushToUser_sendsJsonNotificationToAllSessionsForThatUser() throws Exception {
        when(repository.countByTenantIdAndRecipientAndReadFalse(1L, "alice")).thenReturn(0L);
        WebSocketSession s1 = session("alice");
        WebSocketSession s2 = session("alice");
        WebSocketSession bobs = session("bob");
        handler.afterConnectionEstablished(s1);
        handler.afterConnectionEstablished(s2);
        handler.afterConnectionEstablished(bobs);

        NotificationEntity notification = NotificationEntity.builder()
                .id(42L)
                .recipient("alice")
                .type("MEASURE_SUBMITTED")
                .title("test")
                .message("hello")
                .link("/measures/1")
                .createdAt(LocalDateTime.of(2026, 5, 21, 0, 0))
                .build();

        handler.pushToUser("alice", notification);

        ArgumentCaptor<WebSocketMessage<?>> captor1 = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(s1, times(2)).sendMessage(captor1.capture()); // initial unread + notification
        TextMessage notifMessage = (TextMessage) captor1.getAllValues().get(1);
        assertThat(notifMessage.getPayload()).contains("\"type\":\"notification\"");
        assertThat(notifMessage.getPayload()).contains("\"id\":42");
        assertThat(notifMessage.getPayload()).contains("\"notificationType\":\"MEASURE_SUBMITTED\"");

        verify(s2, times(2)).sendMessage(any(WebSocketMessage.class));
        verify(bobs, times(1)).sendMessage(any(WebSocketMessage.class)); // only its own initial unread
    }

    @Test
    void pushToUser_noOpWhenNoSessionsForRecipient() throws Exception {
        NotificationEntity notification = NotificationEntity.builder()
                .id(1L)
                .recipient("ghost")
                .type("X")
                .title("t")
                .createdAt(LocalDateTime.now())
                .build();

        // Must not throw — common case during workflow notifications when the
        // recipient is not currently online.
        handler.pushToUser("ghost", notification);
        assertThat(handler.sessionCount("ghost")).isZero();
    }

    @Test
    void sendKeepalivePings_emitsProtocolPingToEachOpenSession() throws Exception {
        when(repository.countByTenantIdAndRecipientAndReadFalse(1L, "alice")).thenReturn(0L);
        WebSocketSession s1 = session("alice");
        WebSocketSession s2 = session("bob");
        handler.afterConnectionEstablished(s1);
        handler.afterConnectionEstablished(s2);

        handler.sendKeepalivePings();

        // Each session received: initial unread (Text) + one Ping.
        verify(s1, atLeastOnce()).sendMessage(any(PingMessage.class));
        verify(s2, atLeastOnce()).sendMessage(any(PingMessage.class));
    }

    @Test
    void sendKeepalivePings_skipsClosedSessions() throws Exception {
        when(repository.countByTenantIdAndRecipientAndReadFalse(1L, "alice")).thenReturn(0L);
        WebSocketSession s = session("alice");
        handler.afterConnectionEstablished(s);
        when(s.isOpen()).thenReturn(false);

        handler.sendKeepalivePings();

        verify(s, never()).sendMessage(any(PingMessage.class));
    }

    @Test
    void sendKeepalivePings_noOpWhenNoSubscribers() {
        // Fast path: handler.sendKeepalivePings should return immediately
        // when no sessions are registered (avoids scheduler overhead in tests
        // that load the full context but never call afterConnectionEstablished).
        handler.sendKeepalivePings(); // no-throw
    }
}
