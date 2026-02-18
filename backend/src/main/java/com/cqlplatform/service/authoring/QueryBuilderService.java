package com.cqlplatform.service.authoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Service providing FHIR R4 resource property metadata and query operators
 * for the advanced query builder UI. Configuration is loaded from
 * {@code data/queryBuilderConfig.json} on the classpath.
 */
@Service
@Slf4j
public class QueryBuilderService {

    private List<Map<String, Object>> fhirResources = List.of();
    private List<Map<String, Object>> operators = List.of();

    @PostConstruct
    @SuppressWarnings("unchecked")
    void loadConfig() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource("data/queryBuilderConfig.json").getInputStream()) {
            Map<String, Object> config = mapper.readValue(is, new TypeReference<>() {});
            fhirResources = (List<Map<String, Object>>) config.getOrDefault("resources", List.of());
            operators = (List<Map<String, Object>>) config.getOrDefault("operators", List.of());
            log.info("Loaded query builder config: {} resources, {} operators", fhirResources.size(), operators.size());
        } catch (IOException e) {
            log.error("Failed to load queryBuilderConfig.json, using empty defaults", e);
        }
    }

    public List<Map<String, Object>> getResources() {
        return Collections.unmodifiableList(fhirResources);
    }

    public List<Map<String, Object>> getOperators() {
        return Collections.unmodifiableList(operators);
    }

    public Optional<Map<String, Object>> getResource(String name) {
        return fhirResources.stream()
                .filter(r -> name.equals(r.get("name")))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOperatorsForType(String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : operators) {
            List<String> types = (List<String>) op.get("applicableTypes");
            if (types != null && types.contains(type)) {
                result.add(op);
            }
        }
        return result;
    }
}
