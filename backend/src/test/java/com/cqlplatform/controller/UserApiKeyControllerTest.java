package com.cqlplatform.controller;

import com.cqlplatform.entity.UserApiKeyEntity;
import com.cqlplatform.service.UserApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserApiKeyService apiKeyService;

    private UserApiKeyEntity createKeyEntity(Long id, String name) {
        return UserApiKeyEntity.builder()
                .id(id)
                .username("testuser")
                .apiKey("cql_abc123def456ghi789")
                .name(name)
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();
    }

    @Test
    void listKeys_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/user/api-keys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void listKeys_shouldReturnMaskedKeys() throws Exception {
        when(apiKeyService.listKeys("testuser")).thenReturn(List.of(createKeyEntity(1L, "My Key")));

        mockMvc.perform(get("/api/user/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Key"))
                .andExpect(jsonPath("$[0].keyPreview").exists())
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateKey_shouldReturn201WithFullKey() throws Exception {
        UserApiKeyEntity key = createKeyEntity(1L, "New Key");
        when(apiKeyService.generateApiKey("testuser", "New Key")).thenReturn(key);

        mockMvc.perform(post("/api/user/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Key"))
                .andExpect(jsonPath("$.key").value("cql_abc123def456ghi789"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateKey_noName_shouldUseDefault() throws Exception {
        UserApiKeyEntity key = createKeyEntity(1L, "Unnamed Key");
        when(apiKeyService.generateApiKey("testuser", "Unnamed Key")).thenReturn(key);

        mockMvc.perform(post("/api/user/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "testuser")
    void revokeKey_success_shouldReturn200() throws Exception {
        when(apiKeyService.revokeKey(1L, "testuser")).thenReturn(true);

        mockMvc.perform(delete("/api/user/api-keys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("API key revoked"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void revokeKey_notFound_shouldReturn404() throws Exception {
        when(apiKeyService.revokeKey(999L, "testuser")).thenReturn(false);

        mockMvc.perform(delete("/api/user/api-keys/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
