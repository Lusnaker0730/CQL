package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.CqlBuildResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CqlArtifactBuilderTest {

    private final CqlArtifactBuilder builder = new CqlArtifactBuilder();

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    @Test
    void buildCql_emptyTree_shouldProduceValidLibraryHeader() {
        CqlBuildResult result = builder.buildCql(
                "TestLib", "1.0.0",
                emptyTree(), emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        assertThat(result.cql()).contains("library TestLib version '1.0.0'");
        assertThat(result.cql()).contains("using FHIR");
        assertThat(result.cql()).contains("context Patient");
        assertThat(result.hasWarnings()).isFalse();
    }

    @Test
    void buildCql_unknownElementType_shouldProduceWarning() {
        Map<String, Object> tree = emptyTree();
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("type", "SomeFutureType");
        child.put("name", "MyElement");
        child.put("fields", List.of(Map.of("id", "element_name", "value", "MyElement")));
        List<Map<String, Object>> children = new ArrayList<>();
        children.add(child);
        tree.put("childInstances", children);

        CqlBuildResult result = builder.buildCql(
                "TestLib", "1.0.0",
                tree, emptyTree(),
                List.of(), List.of(), List.of(),
                null, List.of(), "R4"
        );

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Unknown element type 'SomeFutureType'"));
    }

    @Test
    void currentBaseElements_instanceField_shouldNotExist() {
        // Structural assertion: the mutable instance field has been removed
        List<String> instanceFieldNames = new ArrayList<>();
        for (Field f : CqlArtifactBuilder.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                instanceFieldNames.add(f.getName());
            }
        }
        assertThat(instanceFieldNames).doesNotContain("currentBaseElements");
    }
}
