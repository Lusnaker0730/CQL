package com.cqlplatform.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PAT-167: confirms the WS handshake interceptor reads the principal
 * populated upstream by {@code JwtAuthenticationFilter}'s ticket flow.
 *
 * <p>We test the interceptor in isolation (not through Spring's actual
 * WS handler chain), so the assertion is "given a SecurityContext shape,
 * does the interceptor accept or reject?" — the integration with the
 * filter chain itself is covered by {@code SecurityConfig} + existing
 * {@code JwtAuthenticationFilterTest} test bodies.
 */
class WebSocketConfigHandshakeTest {

    private final WebSocketConfig.TicketHandshakeInterceptor interceptor =
            new WebSocketConfig.TicketHandshakeInterceptor();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void beforeHandshake_acceptsWhenSecurityContextHasAuthenticatedUser() {
        var auth = new UsernamePasswordAuthenticationToken(
                "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(WebSocketConfig.USERNAME_ATTR, "alice");
    }

    @Test
    void beforeHandshake_rejectsAnonymousAuth() {
        var anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anon);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);

        boolean accepted = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshake_rejectsEmptySecurityContext() {
        // No setAuthentication — getAuthentication() returns null.
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);

        boolean accepted = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
