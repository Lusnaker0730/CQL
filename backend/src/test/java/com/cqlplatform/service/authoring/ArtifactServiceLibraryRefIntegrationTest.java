package com.cqlplatform.service.authoring;

import com.cqlplatform.model.authoring.ArtifactRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the frontend→backend JSON round-trip for CQL library
 * references (PAT-103). Complements {@link CqlGenerationServiceLibraryIntegrationTest}
 * (PAT-102), which persists via the JPA repository and skips the DTO layer.
 *
 * <p>This test exercises the full REST-shaped path that the new CDS
 * {@code LibraryDefinitionPicker} triggers:
 * <pre>
 *   frontend factory (libraryReferenceToElement)
 *     → JSON POST body
 *     → Jackson deserializes to ArtifactRequest
 *     → ArtifactService.requestToEntity() maps fields
 *     → entity @PrePersist serializes the Map tree to JSON columns
 *     → save
 *     → CqlGenerationService reads back, @PostLoad deserializes
 *     → CqlArtifactBuilder.buildCql() walks tree and emits CQL
 * </pre>
 *
 * <p>If any layer silently strips fields (e.g. a future DTO becomes typed and
 * filters unknown node types, or an `@JsonProperty` renaming mismatches the
 * frontend field ids), the generated CQL would miss the include statement and
 * no unit test would flag it. This round-trip catches that class of regression.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArtifactServiceLibraryRefIntegrationTest {

    @Autowired
    private ArtifactService artifactService;
    @Autowired
    private CqlGenerationService cqlGenerationService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Exact JSON body the frontend factory would POST — matches
     * {@code libraryReferenceToElement(reference)} in the TS util, wrapped in
     * an inclusion tree. Field ids (libraryName / libraryVersion / alias /
     * definitionName) must match what {@code ExpressionCqlEngine} reads.
     */
    private static final String FRONTEND_REQUEST_JSON = """
        {
          "name": "FrontendPickerTest",
          "version": "1.0.0",
          "status": "draft",
          "expTreeInclude": {
            "id": "And",
            "name": "And",
            "conjunction": true,
            "returnType": "boolean",
            "childInstances": [
              {
                "uniqueId": "libref-xyz123",
                "type": "externalCqlElement",
                "name": "shared.\\"HasDiabetes\\"",
                "returnType": "boolean",
                "fields": [
                  {"id": "libraryName",    "type": "string", "name": "Library Name",    "value": "SharedLogic",  "static": true},
                  {"id": "libraryVersion", "type": "string", "name": "Library Version", "value": "1.2.0",        "static": true},
                  {"id": "definitionName", "type": "string", "name": "Definition Name", "value": "HasDiabetes",  "static": true},
                  {"id": "alias",          "type": "string", "name": "Alias",           "value": "shared",       "static": true}
                ],
                "modifiers": []
              }
            ]
          }
        }
        """;

    @Test
    void frontendPickerJson_deserializesAndGeneratesCqlWithInclude() throws Exception {
        ArtifactRequest request = objectMapper.readValue(FRONTEND_REQUEST_JSON, ArtifactRequest.class);
        var saved = artifactService.create(request, "test-user");

        String cql = cqlGenerationService.generateCql(saved.getId());

        // Top-level include statement — proves libraryName/libraryVersion/alias fields
        // survived Jackson → DTO → entity → @PrePersist → @PostLoad round-trip.
        assertThat(cql)
                .as("picker-generated JSON must produce an include statement")
                .contains("include SharedLogic version '1.2.0' called shared");

        // Body reference — proves definitionName survived the same path AND
        // ExpressionCqlEngine emits the right "alias"."DefName" shape.
        assertThat(cql)
                .as("picker-generated JSON must produce alias.defName body reference")
                .contains("\"shared\".\"HasDiabetes\"");
    }

    @Test
    void emptyTree_regression_stillBuildsCqlWithoutLibraryIncludes() throws Exception {
        // Sanity guard: an artifact with no library refs must still produce CQL
        // without spurious include statements. If the picker path accidentally
        // injects something unconditionally, this catches it.
        String minimalJson = """
            {
              "name": "NoLibsArtifact",
              "version": "1.0.0",
              "status": "draft",
              "expTreeInclude": {
                "id": "And", "name": "And", "conjunction": true,
                "returnType": "boolean", "childInstances": []
              }
            }
            """;
        ArtifactRequest request = objectMapper.readValue(minimalJson, ArtifactRequest.class);
        var saved = artifactService.create(request, "test-user");

        String cql = cqlGenerationService.generateCql(saved.getId());

        // Built-in includes (FHIRHelpers, C3F) must still be present
        assertThat(cql).contains("include FHIRHelpers");
        assertThat(cql).contains("include CDSConnectCommonsForFHIRv401");
        // But nothing library-ref-shaped slipped in
        assertThat(cql).doesNotContain("SharedLogic");
    }
}
