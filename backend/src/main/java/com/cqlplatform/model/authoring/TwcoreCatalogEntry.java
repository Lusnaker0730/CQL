package com.cqlplatform.model.authoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TwcoreCatalogEntry {
    private String resourceType;
    private String name;
    private String url;
    private String system;
    private List<TwcoreCodeCategory> categories;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwcoreCodeCategory {
        private String name;
        private List<TwcoreCode> codes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwcoreCode {
        private String code;
        private String display;
        private String displayZh;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwcoreCodeSystem {
        private String label;
        private String url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwcoreCatalog {
        private List<TwcoreCatalogEntry> valueSets;
        private List<TwcoreCodeSystem> codeSystems;
    }
}
