package com.cqlplatform.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "cds_external_cql_library")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsExternalCqlLibraryEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "fhir_version", length = 20)
    private String fhirVersion;

    @Column(name = "cql_content", nullable = false, columnDefinition = "TEXT")
    private String cqlContent;

    @Column(name = "elm_json", columnDefinition = "TEXT")
    private String elmJson;

    @Column(name = "details", columnDefinition = "TEXT")
    private String detailsJson;

    @Transient
    @Builder.Default
    private Map<String, Object> detailsMap = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        serializeAll();
    }

    @PreUpdate
    protected void onUpdate() {
        serializeAll();
    }

    @PostLoad
    protected void onLoad() {
        deserializeAll();
    }

    private void serializeAll() {
        if (detailsMap != null && !detailsMap.isEmpty()) {
            try {
                detailsJson = MAPPER.writeValueAsString(detailsMap);
            } catch (JsonProcessingException e) {
                detailsJson = "{}";
            }
        } else {
            detailsJson = "{}";
        }
    }

    private void deserializeAll() {
        if (detailsJson != null && !detailsJson.isBlank()) {
            try {
                detailsMap = MAPPER.readValue(detailsJson, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                detailsMap = new LinkedHashMap<>();
            }
        } else {
            detailsMap = new LinkedHashMap<>();
        }
    }
}
