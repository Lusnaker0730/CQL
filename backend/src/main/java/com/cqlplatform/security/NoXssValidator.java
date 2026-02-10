package com.cqlplatform.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates that a string does not contain common XSS patterns.
 * Uses the same patterns as {@link XssFilter}.
 */
public class NoXssValidator implements ConstraintValidator<NoXss, String> {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return false;
            }
        }
        return true;
    }
}
