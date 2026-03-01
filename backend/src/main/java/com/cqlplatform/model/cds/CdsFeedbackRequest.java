package com.cqlplatform.model.cds;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsFeedbackRequest {

    @NotEmpty
    @Valid
    @Size(max = 100)
    private List<FeedbackItem> feedback;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackItem {
        @NotBlank
        @Size(max = 200)
        private String card;

        @NotBlank
        @Pattern(regexp = "accepted|overridden")
        private String outcome;

        @Size(max = 50)
        private List<AcceptedSuggestion> acceptedSuggestions;
        private OverrideReason overrideReason;

        @Size(max = 50)
        private String outcomeTimestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcceptedSuggestion {
        @Size(max = 200)
        private String id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverrideReason {
        @Size(max = 200)
        private String code;

        @Size(max = 500)
        private String display;
    }
}
