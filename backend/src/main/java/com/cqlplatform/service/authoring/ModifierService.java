package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.ModifierDefinition;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class ModifierService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private List<ModifierDefinition> modifiers = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/modifiers.json").getInputStream();
            modifiers = MAPPER.readValue(is, new TypeReference<>() {});
            log.info("Loaded {} modifier definitions", modifiers.size());
        } catch (IOException e) {
            log.error("Failed to load modifiers", e);
        }
    }

    public List<ModifierDefinition> getAllModifiers() {
        return modifiers;
    }

    public List<ModifierDefinition> getModifiersByInputType(String inputType) {
        return modifiers.stream()
                .filter(m -> m.getInputTypes() != null && m.getInputTypes().contains(inputType))
                .collect(Collectors.toList());
    }
}
