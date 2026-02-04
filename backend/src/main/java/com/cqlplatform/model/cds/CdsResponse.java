package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsResponse {
    private List<Card> cards;
    private List<SystemAction> systemActions;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Card {
        private String uuid;
        private String summary;
        private String detail;
        private String indicator; // info, warning, critical
        private Source source;
        private List<Suggestion> suggestions;
        private String selectionBehavior;
        private List<Link> links;
        private List<OverrideReason> overrideReasons;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Source {
        private String label;
        private String url;
        private String icon;
        private Coding topic;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Coding {
        private String system;
        private String code;
        private String display;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Suggestion {
        private String uuid;
        private String label;
        private Boolean isRecommended;
        private List<Action> actions;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        private String type; // create, update, delete
        private String description;
        private Object resource;
        private String resourceId;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {
        private String label;
        private String url;
        private String type; // absolute, smart
        private String appContext;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OverrideReason {
        private Coding code;
        private String display;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemAction {
        private String type;
        private String description;
        private Object resource;
        private String resourceId;
    }
}
