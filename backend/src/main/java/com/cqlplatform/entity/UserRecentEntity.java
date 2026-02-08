package com.cqlplatform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_recent_libraries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_recent", columnNames = {"username", "library_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "library_id", nullable = false)
    private Long libraryId;

    @Column(name = "library_name", length = 200)
    private String libraryName;

    @Column(name = "library_version", length = 50)
    private String libraryVersion;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @PrePersist
    protected void onCreate() {
        accessedAt = LocalDateTime.now();
    }
}
