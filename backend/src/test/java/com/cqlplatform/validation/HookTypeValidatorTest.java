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
}
