package com.cqlplatform.controller;

import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private OwnershipVerifier ownershipVerifier;

    private NotificationEntity createNotification(Long id, String recipient) {
        return NotificationEntity.builder()
                .id(id)
                .recipient(recipient)
                .type("INFO")
                .title("Test Notification")
                .message("Test message")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void getNotifications_shouldReturnList() throws Exception {
        when(ownershipVerifier.getCurrentUsername()).thenReturn("testuser");
        when(notificationService.getNotifications("testuser")).thenReturn(List.of(
                createNotification(1L, "testuser"),
                createNotification(2L, "testuser")
        ));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Test Notification"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void getUnreadCount_shouldReturnCount() throws Exception {
        when(ownershipVerifier.getCurrentUsername()).thenReturn("testuser");
        when(notificationService.getUnreadCount("testuser")).thenReturn(5L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void markAsRead_shouldReturnUpdatedNotification() throws Exception {
        NotificationEntity notification = createNotification(1L, "testuser");
        notification.setRead(true);
        when(ownershipVerifier.getCurrentUsername()).thenReturn("testuser");
        when(notificationService.markAsRead(1L, "testuser")).thenReturn(notification);

        mockMvc.perform(post("/api/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void markAllAsRead_shouldReturnUpdatedCount() throws Exception {
        when(ownershipVerifier.getCurrentUsername()).thenReturn("testuser");
        when(notificationService.markAllAsRead("testuser")).thenReturn(3);

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void markAsRead_notFound_shouldReturn400() throws Exception {
        when(ownershipVerifier.getCurrentUsername()).thenReturn("testuser");
        when(notificationService.markAsRead(9999L, "testuser"))
                .thenThrow(new IllegalArgumentException("Notification not found: 9999"));

        mockMvc.perform(post("/api/notifications/9999/read"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Notification not found: 9999"));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
