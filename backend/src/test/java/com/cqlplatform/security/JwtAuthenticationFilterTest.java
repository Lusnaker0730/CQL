package com.cqlplatform.security;

import com.cqlplatform.service.TokenVersionService;
import com.cqlplatform.service.UserApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Locks the auth-rejection invariants in {@link JwtAuthenticationFilter}:
 *
 * <ul>
 *   <li>PAT-143 — token from a hard-deleted user must be rejected. The previous
 *       {@code -1} sentinel from {@code TokenVersionService.loadFromDb} made
 *       {@code claimVersion < -1} false → request proceeded as the deleted user.</li>
 *   <li>Stale-claim case (claimVersion {@code <} currentVersion) must reject.</li>
 *   <li>Fresh-claim case must set authentication.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private SseTicketService sseTicketService;
    @Mock private TokenVersionService tokenVersionService;
    @Mock private UserApiKeyService userApiKeyService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtTokenProvider, sseTicketService, tokenVersionService,
                Optional.of(userApiKeyService));
        SecurityContextHolder.clearContext();
        // lenient(): not every test exercises the JWT path (no-Authorization /
        // api-key tests use different header/URI). Avoid Mockito strict-mode
        // UnnecessaryStubbingException on those.
        lenient().when(request.getHeader("Authorization")).thenReturn("Bearer good.jwt.value");
        lenient().when(request.getRequestURI()).thenReturn("/api/measures");
        lenient().when(jwtTokenProvider.validateToken("good.jwt.value")).thenReturn(true);
        lenient().when(jwtTokenProvider.getUsername("good.jwt.value")).thenReturn("alice");
        lenient().when(jwtTokenProvider.getRole("good.jwt.value")).thenReturn("USER");
        lenient().when(jwtTokenProvider.getTokenVersion("good.jwt.value")).thenReturn(0);
    }

    @Test
    void freshToken_shouldSetAuthentication() throws Exception {
        when(tokenVersionService.getCurrentVersion("alice")).thenReturn(0);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        verify(chain).doFilter(request, response);
    }

    @Test
    void staleToken_shouldRejectWithoutSettingAuthentication() throws Exception {
        // claimVersion=0, currentVersion=1 → stale → don't authenticate, but still pass chain
        when(tokenVersionService.getCurrentVersion("alice")).thenReturn(1);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void deletedUser_shouldRejectToken_PAT143_regression() throws Exception {
        // PAT-143: previously TokenVersionService.loadFromDb returned -1 for missing
        // user, so claimVersion(0) < currentVersion(-1) was false → auth proceeded.
        // After the fix, missing user yields Integer.MAX_VALUE so 0 < MAX_VALUE = true
        // → token rejected.
        when(tokenVersionService.getCurrentVersion("alice")).thenReturn(Integer.MAX_VALUE);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("Hard-deleted user's JWT must NOT authenticate")
                .isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_shouldPassThroughWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getParameter("ticket")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokenVersionService);
    }

    @Test
    void apiKeyAuth_perUserCdsEndpoint_shouldUseApiKeyService() throws Exception {
        when(request.getRequestURI()).thenReturn("/cds-services/u/alice/some-service");
        when(userApiKeyService.validateApiKey("good.jwt.value"))
                .thenReturn(Optional.of("alice"));

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        verify(chain).doFilter(request, response);
        // JWT path not consulted when API key matches
        verify(jwtTokenProvider, never()).validateToken(any());
    }
}
