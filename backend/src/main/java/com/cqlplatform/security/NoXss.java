package com.cqlplatform.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that the annotated string does not contain XSS patterns.
 * Rejects (does not silently sanitize) strings with script tags, javascript: URIs,
 * inline event handlers, iframes, or eval() calls.
 * <p>
 * Null values pass validation — use {@code @NotBlank} separately for required fields.
 */
@Documented
@Constraint(validatedBy = NoXssValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoXss {
    String message() default "Input contains potentially unsafe content";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
