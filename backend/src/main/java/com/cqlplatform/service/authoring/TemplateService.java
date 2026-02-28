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
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private List<FormTemplateCategory> categories = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/formTemplates.json").getInputStream();
            List<Map<String, Object>> raw = MAPPER.readValue(is, new TypeReference<>() {});
            categories = raw.stream().map(this::parseCategory).toList();
            log.info("Loaded {} element template categories", categories.size());
        } catch (IOException e) {
            log.error("Failed to load form templates", e);
        }
    }

    public List<FormTemplateCategory> getAllCategories() {
        return categories;
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
