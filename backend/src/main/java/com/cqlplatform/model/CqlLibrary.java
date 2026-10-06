package com.cqlplatform.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CqlLibrary {
    private String id;
    private String name;
    private String version;
    private String cqlContent;
    private String elmJson;
    private String description;
    private String status;
    private List<String> dependencies;
    private String ownerUsername;
    private List<String> sharedWith;
    private String accessLevel;
    /** PAT-253: active edit lock (holder, taken at, lapses at); all null when unlocked. */
    private String lockedBy;
    private LocalDateTime lockedAt;
    private LocalDateTime lockExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
