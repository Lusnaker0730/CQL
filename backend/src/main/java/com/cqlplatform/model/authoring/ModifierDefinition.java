package com.cqlplatform.model.authoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierDefinition {
    private String id;
    private String name;
    private String type;
    private List<String> inputTypes;
    private String returnType;
    private String cqlTemplate;
    private String cqlLibraryFunction;
    private Map<String, Object> values;
    private Map<String, Object> validator;
    private String comparisonOperator;
    /** For DuringMeasurementPeriod: query alias (O/C/E/P/M/MS). */
    private String resourceAlias;
    /** For DuringMeasurementPeriod: pre-formed where clause using resourceAlias. */
    private String whereClause;
}
