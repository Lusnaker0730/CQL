package com.cqlplatform.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank
    @Size(max = 500)
    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Size(max = 500)
    @Column(name = "name_en", length = 500)
    private String nameEn;

    @Size(max = 100)
    @Column(name = "category", length = 100)
    private String category;

    @Size(max = 200)
    @Column(name = "subcategory", length = 200)
    private String subcategory;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @NotBlank
    @Size(max = 50)
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
