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
public class FormTemplate {
    private String id;
    private String name;
    private String type;
    private String returnType;
    private String extendsTemplate;  // "extends" in JSON
    private String template;
    private Boolean conjunction;
    private Boolean suppress;
    private Boolean cannotHaveModifiers;
    private List<String> suppressedModifiers;
    private Map<String, Object> validator;
    private List<Map<String, Object>> fields;
}
