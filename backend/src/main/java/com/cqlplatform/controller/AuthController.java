package com.cqlplatform.controller;

import com.cqlplatform.config.OktaProperties;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.model.auth.*;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import com.cqlplatform.security.RefreshTokenCookieUtil;
import com.cqlplatform.security.SseTicketService;
import com.cqlplatform.service.OktaOidcService;
import com.cqlplatform.service.OktaUserInfo;
import com.cqlplatform.service.PasswordResetService;
import com.cqlplatform.service.RefreshTokenService;
import com.cqlplatform.service.RefreshTokenService.TokenPair;
import com.cqlplatform.service.TokenVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieUtil cookieUtil;
    private final SseTicketService sseTicketService;
    private final TokenVersionService tokenVersionService;

    @Autowired(required = false)
    private OktaOidcService oktaOidcService;

    @Autowired(required = false)
    private OktaProperties oktaProperties;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUsername()));

        TokenPair pair = refreshTokenService.createTokenPair(user);
        cookieUtil.addRefreshTokenCookie(response, pair.refreshToken(),
                jwtTokenProvider.getRefreshExpirationMs());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(pair.accessToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(pair.accessExpiresIn())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserEntity.Role.USER)
                .enabled(true)
                .build();

        // Set email with hash
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmailWithHash(request.getEmail());
        }

        userRepository.save(user);

        TokenPair pair = refreshTokenService.createTokenPair(user);
        cookieUtil.addRefreshTokenCookie(response, pair.refreshToken(),
                jwtTokenProvider.getRefreshExpirationMs());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(pair.accessToken())
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(pair.accessExpiresIn())
                .forcePasswordChange(false)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = cookieUtil.extractRefreshToken(request);
        if (rawToken == null || rawToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No refresh token provided"));
        }

        try {
            TokenPair pair = refreshTokenService.refreshTokens(rawToken);
            cookieUtil.addRefreshTokenCookie(response, pair.refreshToken(),
                    jwtTokenProvider.getRefreshExpirationMs());
            return ResponseEntity.ok(Map.of(
                    "token", pair.accessToken(),
                    "expiresIn", pair.accessExpiresIn()));
        } catch (RefreshTokenService.RefreshTokenReuseException e) {
            log.warn("Refresh token reuse detected: {}", e.getMessage());
            cookieUtil.clearRefreshTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Session compromised — please log in again"));
        } catch (RefreshTokenService.InvalidRefreshTokenException e) {
            cookieUtil.clearRefreshTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // Bump token version to immediately invalidate all outstanding access tokens
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            tokenVersionService.bumpVersion(auth.getName());
        }

        String rawToken = cookieUtil.extractRefreshToken(request);
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenService.revokeByToken(rawToken);
        }
        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/sse-ticket")
    public ResponseEntity<?> issueSseTicket() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");
        String ticket = sseTicketService.issueTicket(username, role);
        return ResponseEntity.ok(Map.of("ticket", ticket));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Map<String, Object> response = new HashMap<>();
        response.put("username", user.getUsername());
        response.put("email", user.getEmail() != null ? user.getEmail() : "");
        response.put("role", user.getRole().name());
        response.put("forcePasswordChange", Boolean.TRUE.equals(user.getForcePasswordChange()));
        response.put("authProvider", user.getAuthProvider().name());
        if (user.getDisplayName() != null) {
            response.put("displayName", user.getDisplayName());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        String baseUrl = getBaseUrl(httpRequest);
        passwordResetService.requestPasswordReset(request.getEmail(), baseUrl);
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(Map.of("message",
                "If an account with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        boolean success = passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Password has been reset successfully."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Invalid or expired reset token. Please request a new password reset."));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean success = passwordResetService.changePassword(
                username, request.getCurrentPassword(), request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
        }
    }

    @GetMapping("/okta/config")
    public ResponseEntity<?> getOktaConfig() {
        if (oktaProperties == null || !oktaProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of("enabled", false));
        }
        return ResponseEntity.ok(Map.of(
                "enabled", true,
                "authorizationEndpoint", oktaProperties.getAuthorizationEndpoint(),
                "clientId", oktaProperties.getClientId(),
                "scopes", "openid profile email"
        ));
    }

    @PostMapping("/okta/callback")
    public ResponseEntity<?> oktaCallback(@Valid @RequestBody OktaCallbackRequest request,
                                          HttpServletResponse response) {
        if (oktaOidcService == null || oktaProperties == null || !oktaProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Okta SSO is not enabled"));
        }

        try {
            OktaUserInfo userInfo = oktaOidcService.exchangeCodeForUser(
                    request.getCode(), request.getRedirectUri(), request.getNonce());

            // JIT Provisioning: find or create user
            UserEntity user = userRepository
                    .findByAuthProviderAndExternalId(UserEntity.AuthProvider.OKTA, userInfo.getSub())
                    .orElse(null);

            if (user == null) {
                try {
                    String username = deriveUsername(userInfo);
                    UserEntity newUser = UserEntity.builder()
                            .username(username)
                            .authProvider(UserEntity.AuthProvider.OKTA)
                            .externalId(userInfo.getSub())
                            .role(UserEntity.Role.USER)
                            .enabled(true)
                            .build();
                    if (userInfo.getEmail() != null) {
                        newUser.setEmailWithHash(userInfo.getEmail());
                    }
                    if (userInfo.getName() != null) {
                        newUser.setDisplayName(userInfo.getName());
                    }
                    log.info("JIT provisioning new Okta user: {}", username);
                    user = userRepository.save(newUser);
                } catch (DataIntegrityViolationException e) {
                    // Concurrent JIT provisioning — retry lookup
                    user = userRepository
                            .findByAuthProviderAndExternalId(UserEntity.AuthProvider.OKTA, userInfo.getSub())
                            .orElseThrow(() -> new RuntimeException("Failed to provision Okta user"));
                }
            }

            // Update display name / email if changed in Okta
            boolean updated = false;
            if (userInfo.getName() != null && !userInfo.getName().equals(user.getDisplayName())) {
                user.setDisplayName(userInfo.getName());
                updated = true;
            }
            if (userInfo.getEmail() != null && !userInfo.getEmail().equals(user.getEmail())) {
                user.setEmailWithHash(userInfo.getEmail());
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
            }

            // Check if user is enabled
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "User account is disabled"));
            }

            TokenPair pair = refreshTokenService.createTokenPair(user);
            cookieUtil.addRefreshTokenCookie(response, pair.refreshToken(),
                    jwtTokenProvider.getRefreshExpirationMs());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(pair.accessToken())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .expiresIn(pair.accessExpiresIn())
                    .forcePasswordChange(false)
                    .build());

        } catch (Exception e) {
            log.error("Okta SSO callback failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SSO authentication failed"));
        }
    }

    private String deriveUsername(OktaUserInfo userInfo) {
        String base;
        if (userInfo.getPreferredUsername() != null && !userInfo.getPreferredUsername().isBlank()) {
            base = userInfo.getPreferredUsername().split("@")[0];
        } else if (userInfo.getEmail() != null && !userInfo.getEmail().isBlank()) {
            base = userInfo.getEmail().split("@")[0];
        } else {
            base = "okta_" + userInfo.getSub();
        }
        // Ensure uniqueness
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private String getBaseUrl(HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        // Fallback to request headers — only safe behind trusted reverse proxy
        log.warn("APP_BASE_URL not configured — deriving base URL from request headers (unsafe in production)");
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) host = request.getHeader("Host");
        if (host == null) host = request.getServerName() + ":" + request.getServerPort();
        return scheme + "://" + host;
    }
}
