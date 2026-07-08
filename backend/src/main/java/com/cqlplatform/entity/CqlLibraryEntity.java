package com.cqlplatform.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cql_library")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlLibraryEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "version", nullable = false, length = 50)
    private String version;

    @Column(name = "cql_content", columnDefinition = "TEXT", nullable = false)
    private String cqlContent;

    @Column(name = "elm_json", columnDefinition = "TEXT")
    private String elmJson;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = com.cqlplatform.model.measure.MeasureStatusConstants.ACTIVE;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

    @Column(name = "shared_with", columnDefinition = "TEXT")
    @Builder.Default
    private String sharedWith = "[]";

    @Column(name = "access_level", length = 20)
    @Builder.Default
    private String accessLevel = "private";

    /**
     * Tenant (clinic) that owns this library — the isolation boundary (Phase 2).
     * Assigned server-side; existing rows were backfilled to the default tenant. Nullable
     * for now (foundation); the enforcement PR makes it NOT NULL and scopes all reads.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "dependencies", columnDefinition = "TEXT")
    private String dependencies;

    @Transient
    @Builder.Default
    private List<String> dependencyList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<String> sharedWithList = new ArrayList<>();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeDependencies();
        serializeSharedWith();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeDependencies();
        serializeSharedWith();
    }

    @PostLoad
    protected void onLoad() {
        deserializeDependencies();
        deserializeSharedWith();
    }

    private void serializeDependencies() {
        if (dependencyList != null && !dependencyList.isEmpty()) {
            try {
                dependencies = MAPPER.writeValueAsString(dependencyList);
            } catch (JsonProcessingException e) {
                dependencies = "[]";
            }
        } else {
            dependencies = "[]";
        }
    }

    private void deserializeDependencies() {
        if (dependencies != null && !dependencies.isBlank()) {
            try {
                dependencyList = MAPPER.readValue(dependencies, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                dependencyList = new ArrayList<>();
            }
        } else {
            dependencyList = new ArrayList<>();
        }
    }

    private void serializeSharedWith() {
        if (sharedWithList != null && !sharedWithList.isEmpty()) {
            try {
                sharedWith = MAPPER.writeValueAsString(sharedWithList);
            } catch (JsonProcessingException e) {
                sharedWith = "[]";
            }
        } else {
            sharedWith = "[]";
        }
    }

    private void deserializeSharedWith() {
        if (sharedWith != null && !sharedWith.isBlank()) {
            try {
                sharedWithList = MAPPER.readValue(sharedWith, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                sharedWithList = new ArrayList<>();
            }
        } else {
            sharedWithList = new ArrayList<>();
        }
    }
}
