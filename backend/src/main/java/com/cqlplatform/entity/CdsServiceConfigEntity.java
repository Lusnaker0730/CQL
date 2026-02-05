package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cds_service_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsServiceConfigEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "hook", nullable = false, length = 50)
    private String hook;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "cql_content", columnDefinition = "TEXT")
    private String cqlContent;

    @Column(name = "cql_library_id", length = 100)
    private String cqlLibraryId;

    @Column(name = "default_indicator", length = 20)
    private String defaultIndicator;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "serviceConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<CdsServicePrefetchEntity> prefetchItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addPrefetchItem(CdsServicePrefetchEntity prefetch) {
        prefetchItems.add(prefetch);
        prefetch.setServiceConfig(this);
    }

    public void removePrefetchItem(CdsServicePrefetchEntity prefetch) {
        prefetchItems.remove(prefetch);
        prefetch.setServiceConfig(null);
    }

    public void clearPrefetchItems() {
        prefetchItems.forEach(p -> p.setServiceConfig(null));
        prefetchItems.clear();
    }
}
