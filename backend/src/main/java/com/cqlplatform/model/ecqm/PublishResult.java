package com.cqlplatform.model.ecqm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResult {

    private Long measureDefinitionId;
    private String measureName;
    /** BUG-147: the published measure's version and lifecycle status (a publish never approves). */
    private String measureVersion;
    private String measureStatus;
    /** BUG-147: the logic changed on an approved measure, so it went into a new draft version. */
    private boolean newVersion;
    /** BUG-147: the approved measure the new version will replace once approved. */
    private Long supersedesMeasureId;
    private String cql;
    private String message;
}
