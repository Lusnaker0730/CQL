package com.cqlplatform.model.authoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTemplateCategory {
    private Integer id;
    private String icon;
    private String name;
    private Boolean suppress;
    private List<FormTemplate> entries;
}
