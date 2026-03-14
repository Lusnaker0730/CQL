package com.cqlplatform.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CqlFixSuggestionResponse {
    private boolean success;
    private String explanation;
    private String suggestedCql;
    private String errorMessage;
    private String model;
}
