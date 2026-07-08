package com.cqlplatform.security;

import com.cqlplatform.service.TokenVersionService;
import com.cqlplatform.service.UserApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SseTicketService sseTicketService;
    private final TokenVersionService tokenVersionService;
    private final UserApiKeyService userApiKeyService;

    /**
     * PAT-143 — convert from field-level {@code @Autowired(required=false)} for
     * {@code UserApiKeyService} to constructor injection with {@link Optional<T>},
     * matching the convention established by PAT-138 ({@code CdsHooksService}) and
     * PAT-141 ({@code CqlTranslationService}). Other deps were already constructor-
     * injected via Lombok; the {@code @RequiredArgsConstructor} couldn't accommodate
     * the optional bean, so we hand-write the constructor here.
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    SseTicketService sseTicketService,
                                    TokenVersionService tokenVersionService,
                                    Optional<UserApiKeyService> userApiKeyService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sseTicketService = sseTicketService;
        this.tokenVersionService = tokenVersionService;
        this.userApiKeyService = userApiKeyService.orElse(null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
        String header = request.getHeader("Authorization");
        String requestPath = request.getRequestURI();

        // For SSE endpoints, redeem a one-time ticket instead of exposing
        // the long-lived JWT in a query parameter (which leaks into logs)
        if (header == null && request.getParameter("ticket") != null) {
            var principal = sseTicketService.redeem(request.getParameter("ticket"));
            if (principal.isPresent()) {
                var p = principal.get();
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + p.role()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        p.username(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
                return;
            }
        }

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            // For per-user CDS endpoints, try API key auth first
            if (requestPath.startsWith("/cds-services/u/") && userApiKeyService != null) {
                Optional<String> apiKeyUsername = userApiKeyService.validateApiKey(token);
                if (apiKeyUsername.isPresent()) {
                    String username = apiKeyUsername.get();
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // Standard JWT auth
            if (jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsername(token);
                String role = jwtTokenProvider.getRole(token);
                int claimVersion = jwtTokenProvider.getTokenVersion(token);
                int currentVersion = tokenVersionService.getCurrentVersion(username);

                if (claimVersion < currentVersion) {
                    log.debug("Rejected stale JWT for user {} (claim tv={}, current tv={})",
                            username, claimVersion, currentVersion);
                    filterChain.doFilter(request, response);
                    return;
                }

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Phase 2: expose the caller's tenant for row-level isolation (cleared in finally).
                TenantContext.setCurrentTenantId(jwtTokenProvider.getTenantId(token));
            }
        }

        filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
