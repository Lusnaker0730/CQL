package com.cqlplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @JsonIgnore
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "key_prefix", length = 12)
    private String keyPrefix;

    @Column(name = "name")
    private String name;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
