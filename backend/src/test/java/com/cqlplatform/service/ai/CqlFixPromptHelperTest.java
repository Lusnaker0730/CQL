package com.cqlplatform.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CqlFixPromptHelperTest {

    @Test
    void buildSystemPrompt_emptyList_returnsBasePrompt() {
        String prompt = CqlFixPromptHelper.buildSystemPrompt(List.of());
        assertThat(prompt).isEqualTo(CqlFixPromptHelper.BASE_SYSTEM_PROMPT);
    }

    @Test
    void buildSystemPrompt_nullList_returnsBasePrompt() {
        String prompt = CqlFixPromptHelper.buildSystemPrompt(null);
        assertThat(prompt).isEqualTo(CqlFixPromptHelper.BASE_SYSTEM_PROMPT);
    }

    @Test
    void buildSystemPrompt_withEntries_appendsPatterns() {
        KnowledgeEntry entry = new KnowledgeEntry(
                "cc-match",
                "CodeableConcept Matching",
                KnowledgeEntry.Severity.HIGH,
                List.of("CodeableConcept"),
                "Drill into .coding.",
                List.of(new KnowledgeEntry.Example(
                        "Inline list",
                        "X.code in { \"C1\" }",
                        "exists (X.code.coding C where C in { \"C1\" })"
                ))
        );

        String prompt = CqlFixPromptHelper.buildSystemPrompt(List.of(entry));

        assertThat(prompt).contains(CqlFixPromptHelper.BASE_SYSTEM_PROMPT);
        assertThat(prompt).contains("## RELEVANT PATTERNS FOR THIS ERROR");
        assertThat(prompt).contains("### CodeableConcept Matching");
        assertThat(prompt).contains("Drill into .coding.");
        assertThat(prompt).contains("**Inline list**");
        assertThat(prompt).contains("WRONG:");
        assertThat(prompt).contains("CORRECT:");
        assertThat(prompt).contains("X.code in { \"C1\" }");
    }

    @Test
    void buildSystemPrompt_entryWithoutExamples_stillIncludesTopicAndExplanation() {
        KnowledgeEntry entry = new KnowledgeEntry(
                "no-ex", "No Examples Topic", KnowledgeEntry.Severity.LOW,
                List.of("kw"), "Just explanation.", List.of()
        );

        String prompt = CqlFixPromptHelper.buildSystemPrompt(List.of(entry));

        assertThat(prompt).contains("### No Examples Topic");
        assertThat(prompt).contains("Just explanation.");
        assertThat(prompt).doesNotContain("WRONG:");
        assertThat(prompt).doesNotContain("CORRECT:");
    }

    @Test
    void basePrompt_preservesCoreRules() {
        assertThat(CqlFixPromptHelper.BASE_SYSTEM_PROMPT)
                .contains("library MyLib version '1.0.0'")
                .contains("using FHIR version '4.0.1'")
                .contains("include FHIRHelpers version '4.0.1'")
                .contains("STRICT RULES:")
                .contains("SMALLEST change");
    }
}
