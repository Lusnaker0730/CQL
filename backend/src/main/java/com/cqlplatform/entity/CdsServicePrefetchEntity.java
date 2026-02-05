package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cds_service_prefetch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdsServicePrefetchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CdsServiceConfigEntity serviceConfig;

    @Column(name = "prefetch_key", nullable = false, length = 100)
    private String prefetchKey;

    @Column(name = "query", nullable = false, length = 500)
    private String query;
}
