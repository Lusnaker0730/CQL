package com.cqlplatform.service.cql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CqlTranslationService#hintForError(String)} — the only piece
 * that's a pure function, so we test it without spinning up the Spring context.
 *
 * <p>The patterns under test come from real CDS authoring mistakes (smoke scenario 11
 * hit the CodeableConcept case while being built). Any new pattern added to
 * {@code hintForError} should get a test here so its contract is locked.
 */
class CqlTranslationServiceHintTest {

    @Test
    void codeableConceptEquivalentString_shouldSuggestCodeExtraction() {
        String translatorMsg = "Could not resolve call to operator Equivalent with signature "
                + "(FHIR.CodeableConcept, System.String).";
        String hint = CqlTranslationService.hintForError(translatorMsg);
        assertThat(hint).isNotNull();
        assertThat(hint).contains("CodeableConcept")
                .contains("coding[0].code")
                .contains("Code 'active' from");
    }

    @Test
    void codingEquivalentString_shouldSuggestCodeField() {
        String translatorMsg = "Could not resolve call to operator Equivalent with signature "
                + "(FHIR.Coding, System.String).";
        String hint = CqlTranslationService.hintForError(translatorMsg);
        assertThat(hint).isNotNull();
        assertThat(hint).contains("Coding")
                .contains(".code");
    }

    @Test
    void unrelatedTranslatorError_shouldReturnNullHint() {
        // Don't over-hint. Errors outside the narrow recognized patterns get no hint,
        // so the raw translator message carries through cleanly.
        assertThat(CqlTranslationServiceHint("Expected ';' but found EOF")).isNull();
        assertThat(CqlTranslationServiceHint("Syntax error at line 3")).isNull();
        assertThat(CqlTranslationServiceHint("Library not found: FHIRHelpers")).isNull();
    }

    @Test
    void nullMessage_shouldReturnNullHint() {
        assertThat(CqlTranslationService.hintForError(null)).isNull();
    }

    /** Local shorthand so the unrelated-error test reads cleanly. */
    private String CqlTranslationServiceHint(String msg) {
        return CqlTranslationService.hintForError(msg);
    }
}
