package com.cqlplatform.service.authoring;

import com.cqlplatform.entity.CdsArtifactEntity;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.repository.CdsArtifactRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for CQL library references in CDS authoring artifacts.
 *
 * <p>Earlier layers have unit tests — {@code CqlArtifactBuilderEdgeCaseTest
 * .externalCqlInBaseElements_shouldAddInclude} proves the in-memory builder
 * emits an include statement when fed a base-element tree node with
 * {@code type=externalCqlRef}. This test adds the missing layer: the full
 * persistence + service pipeline.
 *
 * <p>Why that matters: {@link CdsArtifactEntity} serializes the expression
 * tree to JSON via {@code @PrePersist} and deserializes via {@code @PostLoad}.
 * A regression in either hook (e.g. filtering unknown node types, dropping
 * nested {@code fields} arrays, or breaking {@code LinkedHashMap} ordering)
 * would leave {@code CqlArtifactBuilderEdgeCaseTest} green while silently
 * stripping library references at save time. This integration test catches
 * that class of regression by exercising save → load → generate.
 *
 * <p>Confirms the user-visible contract: <em>CDS authoring trees that
 * reference a CQL library emit proper {@code include X version 'Y' called
 * alias} statements and {@code "alias"."DefName"} references in the
 * generated CQL.</em>
 *
 * <p>Scope: backend pipeline only. Out of scope: the frontend UI for
 * inserting library references into the CDS builder tree, which is a
 * separate PR (the LibraryDefinitionPicker used by eCQM isn't wired into
 * CDS ArtifactWorkspace yet).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CqlGenerationServiceLibraryIntegrationTest {

    @Autowired
    private CqlGenerationService cqlGenerationService;
    @Autowired
    private CdsArtifactRepository artifactRepository;

    // BUG-134: artifact lookups are tenant-scoped, so the saved fixture and the caller
    // must sit in the same tenant. No tenant row is needed — tenant_id is a plain column
    // here (the FK lives in the Flyway migration, and Flyway is off for the H2 test profile).
    private static final Long TENANT = 1L;

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenantId(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void externalCqlRefInBaseElements_survivesPersistenceAndEmitsInclude() {
        // User scenario: author picks a stored CQL library (SharedLogic v1.2.0)
        // from a library browser. The builder tree stores a baseElement referencing
        // it via `externalCqlRef`. After save + reload, generated CQL must still
        // contain the include statement.

        CdsArtifactEntity entity = newArtifact("TestLibraryRef");
        entity.setBaseElementsList(List.of(externalCqlRefBaseElement(
                "SharedLogic", "1.2.0", "HasDiabetes")));
        entity = artifactRepository.saveAndFlush(entity);

        String cql = cqlGenerationService.generateCql(entity.getId());

        assertThat(cql)
                .as("persisted externalCqlRef must emit include statement after save/load")
                .contains("include SharedLogic version '1.2.0' called SharedLogic");
    }

    @Test
    void externalCqlElementInInclusionTree_emitsIncludeAndReference() {
        // User scenario: author drops a LibraryDefinitionPicker node (externalCqlElement)
        // directly into the inclusion expression tree. The generated CQL must both:
        //   (a) emit the include statement at library level
        //   (b) reference the definition as "alias"."DefName" in the expression body

        Map<String, Object> picker = externalCqlElementNode(
                "SharedLogic", "1.2.0", "shared", "HasDiabetes");
        Map<String, Object> inclusionTree = treeWithChild(picker);

        CdsArtifactEntity entity = newArtifact("TestLibraryElement");
        entity.setExpTreeIncludeMap(inclusionTree);
        entity = artifactRepository.saveAndFlush(entity);

        String cql = cqlGenerationService.generateCql(entity.getId());

        assertThat(cql)
                .contains("include SharedLogic version '1.2.0' called shared");
        assertThat(cql)
                .as("generated CQL must reference the library def using alias.defName form")
                .contains("\"shared\".\"HasDiabetes\"");
    }

    @Test
    void multipleLibraryReferences_emitOnlyUniqueIncludeStatements() {
        // Two baseElements referencing the SAME library should produce ONE include.
        // A third referencing a DIFFERENT library produces a second include.
        // Guards against duplicate include statements that would make the CQL
        // translator reject the generated output (ambiguous aliases).

        CdsArtifactEntity entity = newArtifact("TestMultiLib");
        entity.setBaseElementsList(List.of(
                externalCqlRefBaseElement("SharedLogic", "1.2.0", "HasDiabetes"),
                externalCqlRefBaseElement("SharedLogic", "1.2.0", "HasHypertension"),
                externalCqlRefBaseElement("CommonUtils", "2.0.0", "IsAdult")));
        entity = artifactRepository.saveAndFlush(entity);

        String cql = cqlGenerationService.generateCql(entity.getId());

        // Both includes present
        assertThat(cql).contains("include SharedLogic version '1.2.0'");
        assertThat(cql).contains("include CommonUtils version '2.0.0'");

        // SharedLogic appears exactly once as an include line (dedup via Set<String>)
        long sharedLogicIncludeCount = cql.lines()
                .filter(line -> line.startsWith("include SharedLogic"))
                .count();
        assertThat(sharedLogicIncludeCount)
                .as("duplicate library references must not produce duplicate include statements")
                .isEqualTo(1);
    }

    @Test
    void regenerateWithoutReSave_producesIdenticalCql() {
        // Deterministic output: fetching the same artifact twice (simulates two
        // `GET /api/authoring/artifacts/{id}/cql` calls) must produce byte-identical
        // CQL. If a transient @PostLoad hook mutates the tree (e.g. adds timestamps
        // or reorders a LinkedHashMap), repeated calls would diverge — this catches
        // that class of drift.

        CdsArtifactEntity entity = newArtifact("TestDeterministic");
        entity.setBaseElementsList(List.of(externalCqlRefBaseElement(
                "SharedLogic", "1.2.0", "HasDiabetes")));
        entity = artifactRepository.saveAndFlush(entity);

        String firstCall = cqlGenerationService.generateCql(entity.getId());
        String secondCall = cqlGenerationService.generateCql(entity.getId());

        assertThat(secondCall).isEqualTo(firstCall);
    }

    @Test
    void generateCqlWithWarnings_exposesLibraryIncludes() {
        // The `generateCqlWithWarnings` path (used by the CQL Builder "Export" flow)
        // also flows through the same builder — verify it yields the same includes
        // so library references surface in that UX too.

        CdsArtifactEntity entity = newArtifact("TestWithWarnings");
        entity.setBaseElementsList(List.of(externalCqlRefBaseElement(
                "SharedLogic", "1.2.0", "HasDiabetes")));
        entity = artifactRepository.saveAndFlush(entity);

        CqlBuildResult result = cqlGenerationService.generateCqlWithWarnings(entity.getId(), "R4");

        assertThat(result.cql()).contains("include SharedLogic version '1.2.0'");
    }

    // ── fixture helpers ────────────────────────────────────────────────

    private CdsArtifactEntity newArtifact(String name) {
        CdsArtifactEntity entity = new CdsArtifactEntity();
        entity.setName(name);
        entity.setVersion("1.0.0");
        entity.setOwnerUsername("test-user");
        entity.setTenantId(TENANT);
        entity.setStatus("draft");
        // Start with empty inclusion/exclusion trees — individual tests layer in
        // their specific tree content via setters.
        entity.setExpTreeIncludeMap(emptyTree());
        entity.setExpTreeExcludeMap(emptyTree());
        return entity;
    }

    private Map<String, Object> emptyTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("id", "And");
        tree.put("name", "And");
        tree.put("conjunction", true);
        tree.put("returnType", "boolean");
        tree.put("childInstances", new ArrayList<>());
        return tree;
    }

    /** Wrap a single node in an And-conjunction tree so the builder walks it. */
    private Map<String, Object> treeWithChild(Map<String, Object> child) {
        Map<String, Object> tree = emptyTree();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get("childInstances");
        children.add(child);
        return tree;
    }

    /** Base-element representation (what ImportCqlDialog stores after importing a library). */
    private Map<String, Object> externalCqlRefBaseElement(String libraryName, String libraryVersion,
                                                          String definitionName) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("uniqueId", "be-" + libraryName + "-" + definitionName);
        node.put("type", "externalCqlRef");
        node.put("name", definitionName);
        node.put("fields", List.of(
                Map.of("id", "library_name", "value", libraryName),
                Map.of("id", "library_version", "value", libraryVersion),
                Map.of("id", "reference_id", "value", libraryName + ":" + definitionName)));
        return node;
    }

    /** Inclusion-tree representation (what LibraryDefinitionPicker produces in eCQM). */
    private Map<String, Object> externalCqlElementNode(String libraryName, String libraryVersion,
                                                       String alias, String definitionName) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "ext-" + alias + "-" + definitionName);
        node.put("uniqueId", "ext-" + alias + "-" + definitionName);
        node.put("name", definitionName);
        node.put("type", "externalCqlElement");
        node.put("returnType", "boolean");
        node.put("fields", List.of(
                Map.of("id", "libraryName", "value", libraryName),
                Map.of("id", "libraryVersion", "value", libraryVersion),
                Map.of("id", "alias", "value", alias),
                Map.of("id", "definitionName", "value", definitionName)));
        return node;
    }
}
