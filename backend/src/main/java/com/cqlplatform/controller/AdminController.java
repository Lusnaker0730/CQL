package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.model.auth.*;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> users = userRepository.findAll().stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserEntity.Role.valueOf(request.getRole()))
                .build();

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmailWithHash(request.getEmail());
        }

        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummary> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody RoleUpdateRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getUsername().equals(currentUsername)) {
            return ResponseEntity.badRequest().build();
        }

        user.setRole(UserEntity.Role.valueOf(request.getRole()));
        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PutMapping("/users/{userId}/enabled")
    public ResponseEntity<UserSummary> updateUserEnabled(
            @PathVariable Long userId,
            @Valid @RequestBody EnabledUpdateRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getUsername().equals(currentUsername)) {
            return ResponseEntity.badRequest().build();
        }

        user.setEnabled(request.getEnabled());
        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(saved));
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
