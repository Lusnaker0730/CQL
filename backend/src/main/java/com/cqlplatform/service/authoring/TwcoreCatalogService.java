package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.TwcoreCatalogEntry;
import com.cqlplatform.model.authoring.TwcoreCatalogEntry.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwcoreCatalogService {

    private final ObjectMapper objectMapper;
    private TwcoreCatalog catalog;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("data/twcoreCatalog.json");
            try (InputStream is = resource.getInputStream()) {
                catalog = objectMapper.readValue(is, TwcoreCatalog.class);
            }
            log.info("Loaded TWCORE catalog: {} value sets, {} code systems",
                    catalog.getValueSets().size(), catalog.getCodeSystems().size());
        } catch (IOException e) {
            log.error("Failed to load TWCORE catalog", e);
            catalog = new TwcoreCatalog();
            catalog.setValueSets(Collections.emptyList());
            catalog.setCodeSystems(Collections.emptyList());
        }
    }

    public List<TwcoreCatalogEntry> getValueSetsForResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return catalog.getValueSets();
        }
        return catalog.getValueSets().stream()
                .filter(e -> resourceType.equalsIgnoreCase(e.getResourceType()))
                .toList();
    }

    public List<TwcoreCodeSystem> getAllCodeSystems() {
        return catalog.getCodeSystems();
    }
}
