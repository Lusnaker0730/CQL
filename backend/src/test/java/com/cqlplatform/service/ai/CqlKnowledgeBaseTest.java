package com.cqlplatform.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that loads real YAML knowledge files from
 * {@code classpath:ai/cql-knowledge/*.yaml} and validates keyword matching.
 */
class CqlKnowledgeBaseTest {

    private CqlKnowledgeBase kb;

    @BeforeEach
    void setUp() {
        kb = new CqlKnowledgeBase();
        kb.load();
    }

    @Test
    void load_shouldIngestAllYamlFiles() {
        assertThat(kb.getEntries()).isNotEmpty();
        assertThat(kb.getEntries()).hasSizeGreaterThan(10);
    }

    @Test
    void allEntries_haveRequiredFields() {
        for (KnowledgeEntry entry : kb.getEntries()) {
            assertThat(entry.id()).as("id").isNotBlank();
            assertThat(entry.topic()).as("topic for " + entry.id()).isNotBlank();
            assertThat(entry.explanation()).as("explanation for " + entry.id()).isNotBlank();
            assertThat(entry.keywords()).as("keywords for " + entry.id()).isNotEmpty();
        }
    }

    @Test
    void allEntryIds_areUnique() {
        List<String> ids = kb.getEntries().stream().map(KnowledgeEntry::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void findRelevant_codeableConceptError_shouldMatchCodeableConceptEntry() {
        String errorMsg = "Could not resolve call to operator In with signature "
                + "(FHIR.CodeableConcept, list<System.Code>)";
        List<KnowledgeEntry> matches = kb.findRelevant(errorMsg, "", 3);

        assertThat(matches).isNotEmpty();
        assertThat(matches)
                .extracting(KnowledgeEntry::id)
                .anyMatch(id -> id.contains("codeable-concept"));
    }

    @Test
    void findRelevant_resolveTypeError_shouldMatchResolveTypeEntry() {
        String errorMsg = "Could not resolve type name 'Patient'";
        List<KnowledgeEntry> matches = kb.findRelevant(errorMsg, "", 3);

        assertThat(matches).isNotEmpty();
        assertThat(matches)
                .extracting(KnowledgeEntry::id)
                .contains("resolve-type-missing-using");
    }

    @Test
    void findRelevant_unrelatedQuery_shouldReturnEmpty() {
        List<KnowledgeEntry> matches = kb.findRelevant(
                "completely unrelated nonsense xyzzy", "", 3);
        assertThat(matches).isEmpty();
    }

    @Test
    void findRelevant_respectsTopK() {
        String broadQuery = "CQL FHIR Patient Observation code resource error";
        List<KnowledgeEntry> top3 = kb.findRelevant(broadQuery, "", 3);
        List<KnowledgeEntry> top1 = kb.findRelevant(broadQuery, "", 1);

        assertThat(top3.size()).isLessThanOrEqualTo(3);
        assertThat(top1.size()).isLessThanOrEqualTo(1);
    }

    @Test
    void findRelevant_twcoreKeywords_shouldMatchTwcoreEntry() {
        String errorMsg = "ICD-10-CM-TW code system not found";
        List<KnowledgeEntry> matches = kb.findRelevant(errorMsg, "", 3);

        assertThat(matches)
                .extracting(KnowledgeEntry::id)
                .anyMatch(id -> id.contains("twcore"));
    }

    @Test
    void findRelevant_nullInputs_shouldNotThrow() {
        List<KnowledgeEntry> matches = kb.findRelevant(null, null, 3);
        assertThat(matches).isEmpty();
    }

    @Test
    void findRelevant_zeroTopK_shouldReturnEmpty() {
        List<KnowledgeEntry> matches = kb.findRelevant("anything", "", 0);
        assertThat(matches).isEmpty();
    }
}
