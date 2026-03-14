package com.cqlplatform.controller;

import com.cqlplatform.entity.NotificationEntity;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification APIs")
public class NotificationController {

    private final NotificationService notificationService;
    private final OwnershipVerifier ownershipVerifier;

    @GetMapping
    @Operation(summary = "Get Notifications", description = "Returns the latest 50 notifications for the current user")
    public ResponseEntity<List<NotificationEntity>> getNotifications() {
        String username = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(notificationService.getNotifications(username));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread Count", description = "Returns the number of unread notifications")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        String username = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(username)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark as Read", description = "Marks a single notification as read")
    public ResponseEntity<NotificationEntity> markAsRead(@PathVariable Long id) {
        String username = ownershipVerifier.getCurrentUsername();
        return ResponseEntity.ok(notificationService.markAsRead(id, username));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark All as Read", description = "Marks all notifications as read for the current user")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        String username = ownershipVerifier.getCurrentUsername();
        int updated = notificationService.markAllAsRead(username);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Notification", description = "Deletes a single notification")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        String username = ownershipVerifier.getCurrentUsername();
        notificationService.deleteNotification(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to Notifications", description = "SSE endpoint for real-time notification push")
    public SseEmitter subscribe() {
        String username = ownershipVerifier.getCurrentUsername();
        return notificationService.subscribe(username);
    }
}
