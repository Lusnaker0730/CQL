package com.cqlplatform.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HookTypeValidatorTest {

    @Test
    void validate_validHookTypes_shouldNotThrow() {
        assertThatCode(() -> HookTypeValidator.validate("patient-view")).doesNotThrowAnyException();
        assertThatCode(() -> HookTypeValidator.validate("order-select")).doesNotThrowAnyException();
        assertThatCode(() -> HookTypeValidator.validate("order-sign")).doesNotThrowAnyException();
        assertThatCode(() -> HookTypeValidator.validate("appointment-book")).doesNotThrowAnyException();
        assertThatCode(() -> HookTypeValidator.validate("encounter-start")).doesNotThrowAnyException();
        assertThatCode(() -> HookTypeValidator.validate("encounter-discharge")).doesNotThrowAnyException();
    }

    @Test
    void validate_invalidHookType_shouldThrow() {
        assertThatThrownBy(() -> HookTypeValidator.validate("invalid-hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type")
                .hasMessageContaining("invalid-hook");
    }

    @Test
    void validate_nullHookType_shouldThrow() {
        assertThatThrownBy(() -> HookTypeValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type");
    }

    @Test
    void validate_blankHookType_shouldThrow() {
        assertThatThrownBy(() -> HookTypeValidator.validate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type");
    }

    @Test
    void validate_deprecatedMedicationPrescribe_shouldSuggestReplacement() {
        // PAT-092: medication-prescribe was deprecated in CDS Hooks 1.0. Rejection message
        // should tell the author which hook to migrate to, not just list valid names
        // (the default "Invalid hook type: xxx. Valid types: [...]" leaves them to google).
        assertThatThrownBy(() -> HookTypeValidator.validate("medication-prescribe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("medication-prescribe")
                .hasMessageContaining("deprecated in CDS Hooks 1.0")
                .hasMessageContaining("order-sign");
    }

    @Test
    void validate_unknownHookType_shouldFallbackToGenericMessage() {
        // Hooks that aren't valid AND aren't on the deprecated list just get the plain
        // "Invalid hook type" message — no false suggestion of a replacement.
        assertThatThrownBy(() -> HookTypeValidator.validate("totally-made-up-hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type")
                .hasMessageContaining("totally-made-up-hook");
    }
}
