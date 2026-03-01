package com.cqlplatform.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "indicator_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorCatalogEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "name_en", length = 500)
    private String nameEn;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "subcategory", length = 200)
    private String subcategory;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
