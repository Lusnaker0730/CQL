package com.cqlplatform.security;

import com.cqlplatform.service.UserApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SseTicketService sseTicketService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UserApiKeyService userApiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

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

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
