package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.FormTemplate;
import com.cqlplatform.model.authoring.FormTemplateCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class TemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> BUILTIN_REFERENCE_TYPES = Set.of(
            "baseElementRef", "parameterRef", "externalCqlRef");

    private List<FormTemplateCategory> categories = new ArrayList<>();
    private Set<String> knownElementTypes = new HashSet<>();

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/formTemplates.json").getInputStream();
            List<Map<String, Object>> raw = MAPPER.readValue(is, new TypeReference<>() {});
            categories = raw.stream().map(this::parseCategory).toList();

            // Resolve template inheritance (e.g. "extends": "Base")
            resolveInheritance();

            Set<String> types = new HashSet<>(BUILTIN_REFERENCE_TYPES);
            for (FormTemplateCategory category : categories) {
                for (FormTemplate entry : category.getEntries()) {
                    if (entry.getId() != null) {
                        types.add(entry.getId());
                    }
                }
            }
            knownElementTypes = types;

            log.info("Loaded {} element template categories ({} element types)",
                    categories.size(), knownElementTypes.size());
        } catch (IOException e) {
            log.error("Failed to load form templates", e);
        }
    }

    public boolean isValidElementType(String type) {
        return knownElementTypes.contains(type);
    }

    public List<FormTemplateCategory> getAllCategories() {
        return categories;
    }

    /**
     * Resolve template inheritance: merge base template fields into child templates.
     * Base fields are prepended so element_name/comment appear first.
     */
    private void resolveInheritance() {
        // Build lookup: template id → template
        Map<String, FormTemplate> lookup = new java.util.HashMap<>();
        for (FormTemplateCategory category : categories) {
            for (FormTemplate entry : category.getEntries()) {
                if (entry.getId() != null) {
                    lookup.put(entry.getId(), entry);
                }
            }
        }

        // Merge base fields into children
        for (FormTemplateCategory category : categories) {
            for (FormTemplate entry : category.getEntries()) {
                String parentId = entry.getExtendsTemplate();
                if (parentId == null) continue;
                FormTemplate parent = lookup.get(parentId);
                if (parent == null || parent.getFields() == null) continue;

                List<Map<String, Object>> childFields = entry.getFields() != null
                        ? entry.getFields() : new ArrayList<>();

                // Collect child field IDs to avoid duplicates
                Set<String> childFieldIds = new HashSet<>();
                for (Map<String, Object> f : childFields) {
                    Object id = f.get("id");
                    if (id != null) childFieldIds.add(id.toString());
                }

                // Prepend parent fields that are not already in child
                List<Map<String, Object>> merged = new ArrayList<>();
                for (Map<String, Object> pf : parent.getFields()) {
                    Object id = pf.get("id");
                    if (id != null && !childFieldIds.contains(id.toString())) {
                        merged.add(pf);
                    }
                }
                merged.addAll(childFields);
                entry.setFields(merged);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private FormTemplateCategory parseCategory(Map<String, Object> raw) {
        FormTemplateCategory category = new FormTemplateCategory();
        category.setId((Integer) raw.get("id"));
        category.setIcon((String) raw.get("icon"));
        category.setName((String) raw.get("name"));
        category.setSuppress((Boolean) raw.get("suppress"));

        List<Map<String, Object>> rawEntries = (List<Map<String, Object>>) raw.getOrDefault("entries", List.of());
        List<FormTemplate> entries = rawEntries.stream().map(this::parseTemplate).toList();
        category.setEntries(entries);

        return category;
    }

    @SuppressWarnings("unchecked")
    private FormTemplate parseTemplate(Map<String, Object> raw) {
        FormTemplate template = new FormTemplate();
        template.setId((String) raw.get("id"));
        template.setName((String) raw.get("name"));
        template.setType((String) raw.get("type"));
        template.setReturnType((String) raw.get("returnType"));
        template.setExtendsTemplate((String) raw.get("extends"));
        template.setTemplate((String) raw.get("template"));
        template.setConjunction((Boolean) raw.get("conjunction"));
        template.setSuppress((Boolean) raw.get("suppress"));
        template.setCannotHaveModifiers((Boolean) raw.get("cannotHaveModifiers"));
        template.setTwcoreOnly((Boolean) raw.get("twcoreOnly"));
        template.setSuppressedModifiers((List<String>) raw.get("suppressedModifiers"));
        template.setValidator((Map<String, Object>) raw.get("validator"));
        template.setFields((List<Map<String, Object>>) raw.get("fields"));

        return template;
    }
}
