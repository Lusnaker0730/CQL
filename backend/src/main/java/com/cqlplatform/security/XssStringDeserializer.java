package com.cqlplatform.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Jackson deserializer that sanitizes all incoming JSON string values
 * by stripping common XSS patterns. Provides defense-in-depth beyond
 * field-level {@link NoXss} validation.
 * <p>
 * Fields that legitimately contain code (CQL, FHIR JSON, etc.) should be
 * annotated with {@code @JsonDeserialize(using = JsonDeserializer.None.class)}
 * to bypass this global sanitizer.
 */
public class XssStringDeserializer extends JsonDeserializer<String> {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<svg[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<math[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<base[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
    };

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null) {
            return null;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            value = pattern.matcher(value).replaceAll("");
        }
        return value;
    }
}
