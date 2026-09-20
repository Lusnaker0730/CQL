package com.cqlplatform.model.terminology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAT-230 — a value set owned by this installation: an explicit list of codes under a canonical
 * URL and a business version. List responses leave {@code concepts} out.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformValueSet {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RETIRED = "retired";
    public static final String ORIGIN_AUTHORED = "authored";
    public static final String ORIGIN_IMPORTED = "imported";

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    /** Canonical URL. Left blank on create, it is derived from the name and FHIR_CANONICAL_BASE. */
    private String url;
    private String version;
    /** Computer-friendly name — also the default CQL identifier. */
    private String name;
    private String title;
    private String description;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;
    private String publisher;
    private List<Concept> concepts;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer conceptCount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String origin;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String ownerUsername;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;
    /** The FHIR resource this was imported from; never sent to clients, used for verbatim re-export. */
    @JsonIgnore
    private String sourceJson;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Concept {
        private String system;
        /** Code system version, when the author pinned one. */
        private String version;
        private String code;
        private String display;
    }
}
