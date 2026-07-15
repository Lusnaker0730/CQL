package com.cqlplatform.entity;

import com.cqlplatform.model.authoring.AuthoringConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "cds_artifact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class CdsArtifactEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_TREE = "{\"id\":\"And\",\"name\":\"And\",\"conjunction\":true,\"returnType\":\"boolean\",\"childInstances\":[]}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant (clinic) that owns this artifact — the isolation boundary (BUG-134). Assigned
     * server-side; existing rows were backfilled to their owner's tenant (default for legacy
     * accounts) in V68. Every lookup must filter on it: OwnershipVerifier's ROLE_ADMIN bypass
     * does not consult TenantContext, so this column is what keeps a clinic ADMIN inside
     * their own tenant.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = AuthoringConstants.DEFAULT_VERSION;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = AuthoringConstants.DEFAULT_STATUS;

    @Column(name = "fhir_version", length = 20)
    @Builder.Default
    private String fhirVersion = AuthoringConstants.DEFAULT_FHIR_VERSION;

    // CPG Metadata
    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "publisher", length = 255)
    private String publisher;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "usage_info", columnDefinition = "TEXT")
    private String usageInfo;

    @Column(name = "copyright", columnDefinition = "TEXT")
    private String copyright;

    @Column(name = "experimental")
    @Builder.Default
    private Boolean experimental = AuthoringConstants.DEFAULT_EXPERIMENTAL;

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    @Column(name = "effective_period_start")
    private LocalDate effectivePeriodStart;

    @Column(name = "effective_period_end")
    private LocalDate effectivePeriodEnd;

    @Column(name = "strength_of_recommendation", columnDefinition = "TEXT")
    private String strengthOfRecommendation;

    @Column(name = "quality_of_evidence", columnDefinition = "TEXT")
    private String qualityOfEvidence;

    @Column(name = "context", columnDefinition = "TEXT")
    private String contextJson;

    @Column(name = "topic", columnDefinition = "TEXT")
    private String topicJson;

    @Column(name = "author", columnDefinition = "TEXT")
    private String authorJson;

    @Column(name = "reviewer", columnDefinition = "TEXT")
    private String reviewerJson;

    @Column(name = "endorser", columnDefinition = "TEXT")
    private String endorserJson;

    @Column(name = "related_artifact", columnDefinition = "TEXT")
    private String relatedArtifactJson;

    // Expression Trees
    @Column(name = "exp_tree_include", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String expTreeInclude = DEFAULT_TREE;

    @Column(name = "exp_tree_exclude", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String expTreeExclude = DEFAULT_TREE;

    // Linked data
    @Column(name = "recommendations", columnDefinition = "TEXT")
    @Builder.Default
    private String recommendationsJson = "[]";

    @Column(name = "subpopulations", columnDefinition = "TEXT")
    @Builder.Default
    private String subpopulationsJson = "[]";

    @Column(name = "base_elements", columnDefinition = "TEXT")
    @Builder.Default
    private String baseElementsJson = "[]";

    @Column(name = "parameters", columnDefinition = "TEXT")
    @Builder.Default
    private String parametersJson = "[]";

    @Column(name = "error_statement", columnDefinition = "TEXT")
    private String errorStatementJson;

    // Ownership
    @Column(name = "owner_username", nullable = false, length = 255)
    private String ownerUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    // Transient fields for deserialized JSON
    @Transient
    @Builder.Default
    private List<Map<String, Object>> contextList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> topicList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> authorList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> reviewerList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> endorserList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> relatedArtifactList = new ArrayList<>();

    @Transient
    @Builder.Default
    private Map<String, Object> expTreeIncludeMap = new LinkedHashMap<>();

    @Transient
    @Builder.Default
    private Map<String, Object> expTreeExcludeMap = new LinkedHashMap<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> recommendationsList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> subpopulationsList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> baseElementsList = new ArrayList<>();

    @Transient
    @Builder.Default
    private List<Map<String, Object>> parametersList = new ArrayList<>();

    @Transient
    private Map<String, Object> errorStatementMap;

    @Transient
    @Builder.Default
    private Map<String, Object> strengthOfRecommendationMap = new LinkedHashMap<>();

    @Transient
    @Builder.Default
    private Map<String, Object> qualityOfEvidenceMap = new LinkedHashMap<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        serializeAll();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        serializeAll();
    }

    @PostLoad
    protected void onLoad() {
        deserializeAll();
    }

    /**
     * Serialize all transient map/list fields to their persistent JSON string counterparts.
     * Must be called explicitly before save() when only transient fields have changed,
     * because Hibernate dirty-checking does not track @Transient fields.
     */
    public void serializeAll() {
        contextJson = serializeList(contextList);
        topicJson = serializeList(topicList);
        authorJson = serializeList(authorList);
        reviewerJson = serializeList(reviewerList);
        endorserJson = serializeList(endorserList);
        relatedArtifactJson = serializeList(relatedArtifactList);
        recommendationsJson = serializeList(recommendationsList);
        subpopulationsJson = serializeList(subpopulationsList);
        baseElementsJson = serializeList(baseElementsList);
        parametersJson = serializeList(parametersList);
        expTreeInclude = serializeMap(expTreeIncludeMap, DEFAULT_TREE);
        expTreeExclude = serializeMap(expTreeExcludeMap, DEFAULT_TREE);
        errorStatementJson = errorStatementMap != null ? serializeMap(errorStatementMap, null) : null;
        strengthOfRecommendation = serializeMap(strengthOfRecommendationMap, null);
        qualityOfEvidence = serializeMap(qualityOfEvidenceMap, null);
    }

    private void deserializeAll() {
        contextList = deserializeList(contextJson);
        topicList = deserializeList(topicJson);
        authorList = deserializeList(authorJson);
        reviewerList = deserializeList(reviewerJson);
        endorserList = deserializeList(endorserJson);
        relatedArtifactList = deserializeList(relatedArtifactJson);
        recommendationsList = deserializeList(recommendationsJson);
        subpopulationsList = deserializeList(subpopulationsJson);
        baseElementsList = deserializeList(baseElementsJson);
        parametersList = deserializeList(parametersJson);
        expTreeIncludeMap = deserializeMap(expTreeInclude);
        expTreeExcludeMap = deserializeMap(expTreeExclude);
        errorStatementMap = deserializeMap(errorStatementJson);
        strengthOfRecommendationMap = deserializeMap(strengthOfRecommendation);
        qualityOfEvidenceMap = deserializeMap(qualityOfEvidence);
    }

    private String serializeList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list for entity id={}: {}", id, e.getMessage());
            return "[]";
        }
    }

    private List<Map<String, Object>> deserializeList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize list for entity id={}: {}", id, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String serializeMap(Map<String, Object> map, String defaultVal) {
        if (map == null || map.isEmpty()) return defaultVal;
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize map for entity id={}: {}", id, e.getMessage());
            return defaultVal;
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize map for entity id={}: {}", id, e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
