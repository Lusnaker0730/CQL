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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ModifierService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Legacy modifier IDs that may still exist in saved artifacts. */
    private static final Set<String> LEGACY_MODIFIER_IDS = Set.of(
            "ConvertToMgPerdL_Qty");
    private List<ModifierDefinition> modifiers = new ArrayList<>();
    private Set<String> knownModifierIds = new HashSet<>();

    @PostConstruct
    public void init() {
        try {
            InputStream is = new ClassPathResource("data/modifiers.json").getInputStream();
            modifiers = MAPPER.readValue(is, new TypeReference<>() {});

            Set<String> ids = new HashSet<>();
            for (ModifierDefinition m : modifiers) {
                if (m.getId() != null) {
                    ids.add(m.getId());
                }
            }
            knownModifierIds = ids;

            log.info("Loaded {} modifier definitions ({} unique IDs)", modifiers.size(), knownModifierIds.size());
        } catch (IOException e) {
            log.error("Failed to load modifiers", e);
        }
    }

    public boolean isValidModifierId(String id) {
        return knownModifierIds.contains(id) || LEGACY_MODIFIER_IDS.contains(id);
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
