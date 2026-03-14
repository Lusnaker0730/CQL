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
    private String cql;
    private String message;
}
