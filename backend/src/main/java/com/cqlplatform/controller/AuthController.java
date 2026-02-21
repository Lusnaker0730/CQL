package com.cqlplatform.controller;

import com.cqlplatform.config.OktaProperties;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.auth.*;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import com.cqlplatform.service.OktaOidcService;
import com.cqlplatform.service.OktaUserInfo;
import com.cqlplatform.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired(required = false)
    private OktaOidcService oktaOidcService;

    @Autowired(required = false)
    private OktaProperties oktaProperties;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
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

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .forcePasswordChange(false)
                .build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

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
    public ResponseEntity<?> oktaCallback(@Valid @RequestBody OktaCallbackRequest request) {
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
                    .orElseGet(() -> {
                        // Derive username
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
                        return userRepository.save(newUser);
                    });

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

            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .expiresIn(jwtTokenProvider.getExpirationMs())
                    .forcePasswordChange(false)
                    .build());

        } catch (Exception e) {
            log.error("Okta SSO callback failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "SSO authentication failed: " + e.getMessage()));
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
        // Fallback to request headers (only safe behind trusted reverse proxy)
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) host = request.getHeader("Host");
        if (host == null) host = request.getServerName() + ":" + request.getServerPort();
        return scheme + "://" + host;
    }
}
