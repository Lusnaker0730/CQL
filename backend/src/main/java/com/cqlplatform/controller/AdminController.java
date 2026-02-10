package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.auth.AdminResetPasswordResponse;
import com.cqlplatform.model.auth.UserSummary;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> users = userRepository.findAll().stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<AdminResetPasswordResponse> resetUserPassword(@PathVariable Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String temporaryPassword = passwordResetService.adminResetPassword(userId);

        return ResponseEntity.ok(AdminResetPasswordResponse.builder()
                .temporaryPassword(temporaryPassword)
                .username(user.getUsername())
                .message("Temporary password generated. User will be required to change it on next login.")
                .build());
    }

    private UserSummary toUserSummary(UserEntity user) {
        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }
}
