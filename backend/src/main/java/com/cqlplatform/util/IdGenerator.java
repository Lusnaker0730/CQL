package com.cqlplatform.util;

import java.util.UUID;

/**
 * Centralized ID generation utility.
 * Use this instead of direct {@code UUID.randomUUID().toString()} calls
 * so the generation strategy can be changed in one place if needed.
 */
public final class IdGenerator {

    private IdGenerator() {}

    /** Generate a random UUID string. */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
