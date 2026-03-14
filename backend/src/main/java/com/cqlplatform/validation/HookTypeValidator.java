package com.cqlplatform.validation;

import java.util.Set;

public final class HookTypeValidator {

    public static final Set<String> VALID_HOOKS = Set.of(
            "patient-view",
            "order-select",
            "order-sign",
            "appointment-book",
            "encounter-start",
            "encounter-discharge"
    );

    private HookTypeValidator() {
    }

    public static void validate(String hook) {
        if (hook == null || !VALID_HOOKS.contains(hook)) {
            throw new IllegalArgumentException(
                    "Invalid hook type: '" + hook + "'. Valid types are: " + VALID_HOOKS);
        }
    }
}
