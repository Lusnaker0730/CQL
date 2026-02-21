package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 500)
    @Convert(converter = EncryptionConverter.class)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "force_password_change")
    @Builder.Default
    private Boolean forcePasswordChange = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "department", length = 100)
    private String department;

    public enum Role {
        ADMIN, USER, DEPARTMENT_ADMIN
    }

    public enum AuthProvider {
        LOCAL, OKTA
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Sets both the encrypted email and its SHA-256 hash for lookup.
     */
    public void setEmailWithHash(String email) {
        this.email = email;
        this.emailHash = email != null ? computeEmailHash(email) : null;
    }

    public static String computeEmailHash(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
