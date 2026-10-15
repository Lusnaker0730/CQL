package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PAT-263 — what changed between two versions of a measure, field by field: metadata grouped by
 * section, every group with its populations / stratifiers / observations matched by id, and the
 * CQL as a line diff. {@code metadataChanges} / {@code populationChanges} are the pre-PAT-263
 * sentences, still produced for older clients of {@code GET /version-compare}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasureVersionDiff {

    public static final String ADDED = "added";
    public static final String REMOVED = "removed";
    public static final String CHANGED = "changed";
    public static final String UNCHANGED = "unchanged";

    private Long oldId;
    private Long newId;
    private String name;
    private String oldVersion;
    private String newVersion;
    private String oldStatus;
    private String newStatus;

    private String oldCql;
    private String newCql;
    private boolean cqlChanged;
    /** Line diff of the CQL ({@code equal} / {@code added} / {@code removed}); null when the texts are too large to diff line by line. */
    private List<CqlLine> cqlLines;

    private List<FieldChange> metadata;
    private List<GroupChange> groups;
    private Summary summary;

    private List<String> metadataChanges;
    private List<String> populationChanges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        /** Where the field lives: description, scoring, stewardship, dates, supplementalData, terminology, identity. */
        private String section;
        private String field;
        private String kind;
        private String oldValue;
        private String newValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupChange {
        private String groupId;
        private String kind;
        private List<FieldChange> fields;
        private List<ItemChange> populations;
        private List<ItemChange> stratifiers;
        private List<ItemChange> observations;
    }

    /** One population / stratifier / observation: {@code key} is its type, id or position; values are the CQL expressions. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemChange {
        private String key;
        private String kind;
        private String oldValue;
        private String newValue;
        private String oldDescription;
        private String newDescription;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CqlLine {
        /** equal | added | removed */
        private String type;
        private Integer oldLine;
        private Integer newLine;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int metadata;
        private int groups;
        private int populations;
        private int stratifiers;
        private int observations;
        private int cqlAdded;
        private int cqlRemoved;
        /** metadata + populations + stratifiers + observations + group additions / removals (+1 when the CQL changed). */
        private int total;
    }
}
