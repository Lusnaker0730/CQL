package com.cqlplatform.entity;

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
    private String status = "active";

    @Column(name = "dependencies", columnDefinition = "TEXT")
    private String dependencies;

    @Transient
    @Builder.Default
    private List<String> dependencyList = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeDependencies();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeDependencies();
    }

    @PostLoad
    protected void onLoad() {
        deserializeDependencies();
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
}
