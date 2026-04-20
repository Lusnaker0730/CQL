package com.cqlplatform.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hex digest helper for content-based provenance. Used by
 * {@code MeasureReportService} to fingerprint CQL and ELM at evaluation time so
 * reports can later be linked to the exact bytes that produced them.
 *
 * <p>Not suitable for password hashing (no salt, no KDF) — this is a forensic
 * provenance tool, not a security primitive.
 */
public final class ContentHash {

    private ContentHash() {
    }

    /**
     * Returns the lowercase hex SHA-256 of the UTF-8 encoded input, or {@code null} if
     * input is {@code null}. Empty string hashes to a well-defined value (the SHA-256
     * of an empty byte sequence) rather than null — so "empty CQL" is distinguishable
     * from "no CQL recorded" in the DB.
     */
    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory per JCA spec — would indicate a broken JVM.
            // Re-throw as RuntimeException rather than silently returning null, which
            // would make the missing hash indistinguishable from a null input.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
