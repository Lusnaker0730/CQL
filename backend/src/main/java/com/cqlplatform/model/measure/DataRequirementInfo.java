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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeFilterInfo {
        private String path;
        private String valueSet;
        private String valueSetName;
        private List<CodingInfo> code;
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
