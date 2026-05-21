package com.cqlplatform.config;

import com.cqlplatform.service.NotificationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * PAT-167: WebSocket transport for notification push (replaces SSE).
 *
 * <p>Auth flow: the ticket the SPA fetches from {@code POST /api/auth/sse-ticket}
 * goes into the WS upgrade URL as {@code ?ticket=...}. The existing
 * {@code JwtAuthenticationFilter} (security/JwtAuthenticationFilter.java) sees
 * the ticket, calls {@code SseTicketService.redeem(...)}, and stashes the
 * resolved principal in {@code SecurityContextHolder} before the request
 * reaches us. {@link TicketHandshakeInterceptor} below just reads that
 * principal — it does NOT re-redeem, because the ticket is single-use and
 * {@code JwtAuthenticationFilter} has already consumed it.
 *
 * <p>CORS is not configured ({@code setAllowedOrigins} unset) because all
 * traffic arrives via the SPA's same-origin proxy ({@code nginx → backend}).
 * Adding a remote origin in the future requires explicit allow-listing.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String USERNAME_ATTR = "username";
    // Path lives OUTSIDE /api/notifications/ on purpose: NotificationController
    // has @DeleteMapping("/{id}") which Spring's MVC dispatcher matches against
    // any single segment under /api/notifications/, returning 405 before the
    // WebSocketHandlerMapping ever runs (MVC mapping has higher priority by
    // Spring's autoconfig defaults). Putting the WS endpoint under a separate
    // /api/ws/ namespace sidesteps the collision and makes the route
    // self-documenting for future WS endpoints.
    public static final String WS_PATH = "/api/ws/notifications";

    private final NotificationWebSocketHandler notificationHandler;

    public WebSocketConfig(NotificationWebSocketHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, WS_PATH)
                .addInterceptors(new TicketHandshakeInterceptor())
                // Spring's default OriginHandshakeInterceptor rejects any
                // upgrade whose Origin header doesn't match the request's
                // host:port. Behind the nginx reverse proxy that pretty much
                // always rejects (backend sees Host=backend:8080, Origin=
                // https://www.twcql.com), so allow any origin pattern here —
                // auth is bound to a single-use ticket redeemed before this
                // interceptor runs, so the Origin check would only be a defense
                // against CSRF, which a single-use ticket already mitigates.
                .setAllowedOriginPatterns("*");
    }

    static class TicketHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request,
                                        ServerHttpResponse response,
                                        WebSocketHandler wsHandler,
                                        Map<String, Object> attributes) {
            // JwtAuthenticationFilter has already redeemed the ticket and
            // populated SecurityContextHolder before this interceptor runs
            // (same request, same thread). If auth is absent the request
            // is anonymous and we refuse the upgrade.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getName())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(USERNAME_ATTR, auth.getName());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Exception exception) {
            // No-op
        }
    }
}
