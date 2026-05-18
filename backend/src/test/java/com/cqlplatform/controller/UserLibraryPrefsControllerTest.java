package com.cqlplatform.controller;

import com.cqlplatform.entity.UserFavoriteEntity;
import com.cqlplatform.entity.UserRecentEntity;
import com.cqlplatform.repository.UserFavoriteRepository;
import com.cqlplatform.repository.UserRecentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLibraryPrefsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserFavoriteRepository favoriteRepository;

    @MockitoBean
    private UserRecentRepository recentRepository;

    // ===== Favorites =====

    @Test
    void getFavorites_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/cql/user-prefs/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getFavorites_shouldReturnParsedLibraryIds() throws Exception {
        UserFavoriteEntity fav = UserFavoriteEntity.builder()
                .id(1L)
                .username("testuser")
                .libraryId("FHIRHelpers-4.0.1")
                .createdAt(LocalDateTime.now())
                .build();

        when(favoriteRepository.findByUsernameOrderByCreatedAtDesc("testuser"))
                .thenReturn(List.of(fav));

        mockMvc.perform(get("/api/cql/user-prefs/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libraryId").value("FHIRHelpers-4.0.1"))
                .andExpect(jsonPath("$[0].libraryName").value("FHIRHelpers"))
                .andExpect(jsonPath("$[0].libraryVersion").value("4.0.1"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addFavorite_new_shouldReturn201() throws Exception {
        when(favoriteRepository.existsByUsernameAndLibraryId("testuser", "FHIRHelpers-4.0.1"))
                .thenReturn(false);
        when(favoriteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/cql/user-prefs/favorites/FHIRHelpers-4.0.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Favorited"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addFavorite_alreadyExists_shouldReturn200() throws Exception {
        when(favoriteRepository.existsByUsernameAndLibraryId("testuser", "FHIRHelpers-4.0.1"))
                .thenReturn(true);

        mockMvc.perform(post("/api/cql/user-prefs/favorites/FHIRHelpers-4.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Already favorited"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void removeFavorite_shouldReturn204() throws Exception {
        doNothing().when(favoriteRepository).deleteByUsernameAndLibraryId("testuser", "FHIRHelpers-4.0.1");

        mockMvc.perform(delete("/api/cql/user-prefs/favorites/FHIRHelpers-4.0.1"))
                .andExpect(status().isNoContent());
    }

    // ===== Recent =====

    @Test
    @WithMockUser(username = "testuser")
    void getRecent_shouldReturnRecentLibraries() throws Exception {
        UserRecentEntity recent = UserRecentEntity.builder()
                .id(1L)
                .username("testuser")
                .libraryId("TestLib-1.0.0")
                .libraryName("TestLib")
                .libraryVersion("1.0.0")
                .accessedAt(LocalDateTime.now())
                .build();

        when(recentRepository.findByUsernameOrderByAccessedAtDesc("testuser"))
                .thenReturn(List.of(recent));

        mockMvc.perform(get("/api/cql/user-prefs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libraryId").value("TestLib-1.0.0"))
                .andExpect(jsonPath("$[0].libraryName").value("TestLib"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addRecent_new_shouldReturn201() throws Exception {
        when(recentRepository.findByUsernameAndLibraryId("testuser", "TestLib-1.0.0"))
                .thenReturn(Optional.empty());
        when(recentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recentRepository.findByUsernameOrderByAccessedAtDesc("testuser"))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/cql/user-prefs/recent/TestLib-1.0.0"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Added to recent"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void addRecent_existing_shouldUpdateAccessedAt() throws Exception {
        UserRecentEntity existing = UserRecentEntity.builder()
                .id(1L)
                .username("testuser")
                .libraryId("TestLib-1.0.0")
                .libraryName("TestLib")
                .libraryVersion("1.0.0")
                .accessedAt(LocalDateTime.now().minusDays(1))
                .build();

        when(recentRepository.findByUsernameAndLibraryId("testuser", "TestLib-1.0.0"))
                .thenReturn(Optional.of(existing));
        when(recentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/cql/user-prefs/recent/TestLib-1.0.0"))
                .andExpect(status().isCreated());

        verify(recentRepository).save(argThat(r -> r.getId().equals(1L)));
    }

    @Test
    @WithMockUser(username = "testuser")
    void clearRecent_shouldReturn204() throws Exception {
        doNothing().when(recentRepository).deleteAllByUsername("testuser");

        mockMvc.perform(delete("/api/cql/user-prefs/recent"))
                .andExpect(status().isNoContent());
    }
}
