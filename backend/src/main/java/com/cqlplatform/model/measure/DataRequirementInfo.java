package com.cqlplatform.model.measure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataRequirementInfo {
    private String type;
    private List<String> profile;
    private List<CodeFilterInfo> codeFilter;
    private List<DateFilterInfo> dateFilter;
    /** PAT-121a: only populated on the Patient entry — age/gender constraints
     *  extracted from where clauses across all measure expressions. */
    private PatientFilterInfo patientFilter;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeFilterInfo {
        private String path;
        private String valueSet;
        private String valueSetName;
        private String codeSystemUrl;
        private String codeSystemName;
        private List<CodingInfo> code;
        /** PAT-121b: prefixes collected from {@code StartsWith(Coding.code.value, 'E08')}
         *  patterns. Clinical CQL commonly groups ICD codes by prefix rather than
         *  enumerating each — this field surfaces that intent so callers can either
         *  translate to FHIR search (e.g. {@code code=E08*}) or display as a range. */
        private List<String> codePrefixes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientFilterInfo {
        /** Minimum age (inclusive). Null if unconstrained. */
        private Integer minAge;
        /** Maximum age (inclusive). Null if unconstrained. */
        private Integer maxAge;
        /** {@code "Year"}, {@code "Month"}, etc. — precision the CQL's CalculateAge used. */
        private String ageUnit;
        /** Allowed gender values (from CQL {@code Patient.gender = 'male'} equality). */
        private List<String> gender;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateFilterInfo {
        private String path;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodingInfo {
        private String system;
        private String code;
        private String display;
    }
}
