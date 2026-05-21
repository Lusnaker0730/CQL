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

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerErrorTest {

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

    private UserEntity createUser(Long id, String username) {
        return UserEntity.builder()
                .id(id)
                .username(username)
                .password("encoded")
                .role(UserEntity.Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void createUser_duplicateUsername_shouldReturn409() throws Exception {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing\",\"password\":\"Password1\",\"role\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with username 'existing' already exists"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserRole_notFound_shouldReturn404() throws Exception {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/users/9999/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 9999"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserRole_selfModify_shouldReturn400() throws Exception {
        UserEntity self = createUser(1L, "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        mockMvc.perform(put("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot modify your own account"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_notFound_shouldReturn404() throws Exception {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/users/9999/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 9999"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateUserEnabled_selfDisable_shouldReturn400() throws Exception {
        UserEntity self = createUser(1L, "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        mockMvc.perform(put("/api/admin/users/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot modify your own account"));
    }
}
