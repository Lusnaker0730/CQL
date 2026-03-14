package com.cqlplatform.entity;

import com.cqlplatform.security.EncryptionConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

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

    @JsonIgnore
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

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

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
        return com.cqlplatform.util.DigestUtils.sha256Hex(email.toLowerCase().trim());
    }
}
