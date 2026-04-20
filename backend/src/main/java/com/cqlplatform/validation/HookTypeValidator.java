package com.cqlplatform.validation;

import java.util.Map;
import java.util.Set;

public final class HookTypeValidator {

    /** Derived from HookContextRequirements to avoid maintaining the hook list in two places. */
    public static final Set<String> VALID_HOOKS = HookContextRequirements.getSupportedHooks();

    /**
     * Hook types that were removed from the CDS Hooks spec. We still reject them, but
     * point the author at the modern replacement instead of just listing valid names —
     * a dev migrating from old examples / tutorials / EHR docs would otherwise google
     * to figure out "is medication-prescribe still a thing, what replaced it".
     *
     * <p>Source: CDS Hooks 1.0 release notes (medication-prescribe → order-sign).
     * Add entries here when new hooks are deprecated; keep it narrow — only hooks we
     * have evidence people actually try to use.
     */
    static final Map<String, String> DEPRECATED_HOOKS = Map.of(
            "medication-prescribe", "order-sign"
    );

    private HookTypeValidator() {
    }

    public static void validate(String hook) {
        if (hook == null || hook.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid hook type: '" + hook + "'. Valid types are: " + VALID_HOOKS);
        }
        if (VALID_HOOKS.contains(hook)) {
            return;
        }
        String replacement = DEPRECATED_HOOKS.get(hook);
        if (replacement != null) {
            throw new IllegalArgumentException(String.format(
                    "Hook type '%s' was deprecated in CDS Hooks 1.0 — use '%s' instead. Valid types are: %s",
                    hook, replacement, VALID_HOOKS));
        }
        throw new IllegalArgumentException(
                "Invalid hook type: '" + hook + "'. Valid types are: " + VALID_HOOKS);
    }
}
