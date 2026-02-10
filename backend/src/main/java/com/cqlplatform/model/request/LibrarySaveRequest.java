package com.cqlplatform.model.request;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
public class LibrarySaveRequest {
    /**
     * CQL content — not sanitized because CQL legitimately contains angle brackets.
     */
    @JsonDeserialize(using = JsonDeserializer.None.class)
    private String cql;

    private String description;
}
