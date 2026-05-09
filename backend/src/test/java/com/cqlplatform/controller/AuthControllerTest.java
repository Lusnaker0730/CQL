package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import com.cqlplatform.security.RefreshTokenCookieUtil;
import com.cqlplatform.service.RefreshTokenService;
import com.cqlplatform.service.RefreshTokenService.TokenPair;
import com.cqlplatform.service.TokenVersionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// PAT-157 — opt in to self-registration for these tests; default is off.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "auth.self-registration.enabled=true")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private RefreshTokenCookieUtil cookieUtil;

    @MockBean
    private TokenVersionService tokenVersionService;

    @Test
    void login_validCredentials_shouldReturnToken() throws Exception {
        UserEntity user = UserEntity.builder()
                .username("testuser")
                .password("encoded")
                .role(UserEntity.Role.USER)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("testuser", "password"));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(refreshTokenService.createTokenPair(any())).thenReturn(
                new TokenPair("test-jwt-token", "refresh-raw", 900000L));
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresIn").value(900000));

        verify(cookieUtil).addRefreshTokenCookie(any(), eq("refresh-raw"), eq(604800000L));
    }

    @Test
    void login_invalidCredentials_shouldReturn401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wrong\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_missingFields_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_newUser_shouldReturnToken() throws Exception {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenService.createTokenPair(any())).thenReturn(
                new TokenPair("new-jwt-token", "refresh-raw", 900000L));
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"Password123\",\"email\":\"new@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt-token"));
    }

    @Test
    void register_existingUser_returnsUniformResponse() throws Exception {
        // PAT-157 — endpoint no longer differentiates "username taken" from
        // "self-registration disabled"; both return 200 with the same pending-
        // approval message. Closes CWE-200 user enumeration.
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing\",\"password\":\"Password123\",\"email\":\"existing@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Registration request received")));
    }

    @Test
    void register_shortUsername_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"Password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validuser\",\"password\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void me_authenticated_shouldReturnUserInfo() throws Exception {
        UserEntity user = UserEntity.builder()
                .username("testuser")
                .email("test@example.com")
                .role(UserEntity.Role.USER)
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void me_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_shouldReturnNewToken() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn("old-refresh");
        when(refreshTokenService.refreshTokens("old-refresh")).thenReturn(
                new TokenPair("new-access", "new-refresh", 900000L));
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access"))
                .andExpect(jsonPath("$.expiresIn").value(900000));

        verify(cookieUtil).addRefreshTokenCookie(any(), eq("new-refresh"), eq(604800000L));
    }

    @Test
    void refresh_noCookie_shouldReturn401() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No refresh token provided"));
    }

    @Test
    void refresh_reuseDetected_shouldReturn401AndClearCookie() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn("reused-token");
        when(refreshTokenService.refreshTokens("reused-token"))
                .thenThrow(new RefreshTokenService.RefreshTokenReuseException("reuse detected"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "reused-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Session compromised — please log in again"));

        verify(cookieUtil).clearRefreshTokenCookie(any());
    }

    @Test
    void refresh_expiredToken_shouldReturn401() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn("expired-token");
        when(refreshTokenService.refreshTokens("expired-token"))
                .thenThrow(new RefreshTokenService.InvalidRefreshTokenException("Refresh token expired"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Refresh token expired"));

        verify(cookieUtil).clearRefreshTokenCookie(any());
    }

    @Test
    void logout_withCookie_shouldRevokeAndClear() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn("token-to-revoke");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "token-to-revoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(refreshTokenService).revokeByToken("token-to-revoke");
        verify(cookieUtil).clearRefreshTokenCookie(any());
    }

    @Test
    void logout_noCookie_shouldStillSucceed() throws Exception {
        when(cookieUtil.extractRefreshToken(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(refreshTokenService, never()).revokeByToken(any());
        verify(cookieUtil).clearRefreshTokenCookie(any());
    }
}
