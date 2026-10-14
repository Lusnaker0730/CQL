package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** PAT-260: the value sets a measure's CQL references and how each one resolves today. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureTerminology {
    private Long measureId;
    private String status;
    /** Pins can be changed only while the measure is a draft (they are measure logic). */
    private boolean pinnable;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;
        private String url;
        /** The version the CQL itself names, or null. */
        private String cqlVersion;
        /** The measure's pin for this URL, or null. */
        private String pinnedVersion;
        /** {@code platform} when this installation owns the value set, else {@code external}. */
        private String source;
        /** The version evaluation currently resolves (CQL version, else pin, else newest active); null when external / unresolved. */
        private String resolvedVersion;
        private String resolvedStatus;
        /** True when the evaluation result cannot drift: the CQL or a pin fixes the version. */
        private boolean pinned;
        /** Every version of the value set the platform holds (newest first), for the picker. */
        private List<Version> versions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Version {
        private Long id;
        private String version;
        private String status;
        private Integer conceptCount;
    }

    /** Body of POST /terminology/pin: pin one URL to a version, or (no url) every unpinned platform value set to what it resolves now. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PinRequest {
        private String url;
        private String version;
    }
}
