package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.service.PasswordResetService;
import com.cqlplatform.service.RefreshTokenService;
import com.cqlplatform.service.TokenVersionService;
import com.cqlplatform.service.UserApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserApiKeyService userApiKeyService;

    @MockitoBean
    private TokenVersionService tokenVersionService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private UserEntity createUser(Long id, String username, UserEntity.Role role) {
        return UserEntity.builder()
                .id(id)
                .username(username)
                .password("encoded")
                .role(role)
                .enabled(true)
                .build();
    }

    @Test
    void listUsers_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "regular", roles = {"USER"})
    void listUsers_regularUserAuthenticated_shouldReturn403() throws Exception {
        // PAT-145 regression: a logged-in non-admin must NOT reach admin endpoints.
        // Locks the class-level @PreAuthorize + SecurityConfig path rule together.
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void listUsers_authenticated_shouldReturnUsers() throws Exception {
        UserEntity user = createUser(1L, "testuser", UserEntity.Role.USER);
        when(userRepository.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createUser_validRequest_shouldReturnUser() throws Exception {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"Password123\",\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createUser_duplicateUsername_shouldReturn409() throws Exception {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing\",\"password\":\"Password123\",\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserRole_differentUser_shouldUpdateRole() throws Exception {
        UserEntity user = createUser(2L, "otheruser", UserEntity.Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserRole_selfUpdate_shouldReturn400() throws Exception {
        UserEntity user = createUser(1L, "admin", UserEntity.Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_shouldToggleEnabled() throws Exception {
        UserEntity user = createUser(2L, "otheruser", UserEntity.Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/admin/users/2/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_disable_shouldDeactivateApiKeys() throws Exception {
        UserEntity user = createUser(2L, "otheruser", UserEntity.Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/admin/users/2/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(userApiKeyService).deactivateAllKeys("otheruser");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_enable_shouldNotDeactivateApiKeys() throws Exception {
        UserEntity user = createUser(2L, "otheruser", UserEntity.Role.USER);
        user.setEnabled(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/admin/users/2/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(userApiKeyService, never()).deactivateAllKeys(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_selfDisable_shouldReturn400() throws Exception {
        UserEntity user = createUser(1L, "admin", UserEntity.Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/admin/users/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void resetUserPassword_shouldReturnSuccessWithoutPassword() throws Exception {
        UserEntity user = createUser(2L, "otheruser", UserEntity.Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        // adminResetPassword is now void; the temp password is emailed, not returned in the response
        doNothing().when(passwordResetService).adminResetPassword(2L);

        mockMvc.perform(post("/api/admin/users/2/reset-password"))
                .andExpect(status().isOk())
                // Security (H8): temporary password must NOT appear in the response body
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist())
                .andExpect(jsonPath("$.username").value("otheruser"))
                .andExpect(jsonPath("$.message").exists())
                // Response must carry Cache-Control: no-store to prevent credential caching
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
