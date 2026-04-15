package com.cqlplatform.validation;

import java.util.Set;

public final class HookTypeValidator {

    /** Derived from HookContextRequirements to avoid maintaining the hook list in two places. */
    public static final Set<String> VALID_HOOKS = HookContextRequirements.getSupportedHooks();

    private HookTypeValidator() {
    }

    public static void validate(String hook) {
        if (hook == null || !VALID_HOOKS.contains(hook)) {
            throw new IllegalArgumentException(
                    "Invalid hook type: '" + hook + "'. Valid types are: " + VALID_HOOKS);
        }
    }
}
