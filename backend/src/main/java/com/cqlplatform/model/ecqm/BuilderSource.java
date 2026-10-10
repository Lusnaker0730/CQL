package com.cqlplatform.model.ecqm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PAT-238 — the eCQM builder artifact a measure was published from, and how far the two have
 * drifted since that publish. {@code measureEditedSincePublish} / {@code builderChangedSincePublish}
 * are null when unknown (published before publish provenance was recorded).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuilderSource {
    private Long artifactId;
    private String artifactName;
    private String artifactVersion;
    private String ownerUsername;
    private LocalDateTime publishedAt;
    private LocalDateTime artifactUpdatedAt;
    /** The measure's CQL / population mapping no longer matches what the builder published. */
    private Boolean measureEditedSincePublish;
    /** The artifact was saved after it was last published (the measure lags the builder). */
    private Boolean builderChangedSincePublish;
}
