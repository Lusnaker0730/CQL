package com.cqlplatform.util;

import java.time.LocalDateTime;

/**
 * PAT-253 — the one edit-lock rule, shared by measures, test cases and CQL libraries.
 *
 * <p>A lock is a ({@code lockedBy}, {@code lockedAt}) pair on the row. It is <em>active</em> while
 * a holder is set and less than {@code timeoutMinutes} ({@code measure.locking.timeout-minutes})
 * have passed since it was taken; an expired lock is treated exactly like no lock, so a holder
 * who walked away cannot block anyone for good. Re-locking by the holder refreshes
 * {@code lockedAt}. Writes by anyone but the holder of an active lock are refused with
 * {@link com.cqlplatform.exception.ResourceLockedException} (HTTP 409 {@code Locked}).
 */
public final class EditLock {

    private EditLock() {
    }

    /** True while the lock has a holder and has not expired. */
    public static boolean isActive(String lockedBy, LocalDateTime lockedAt, int timeoutMinutes) {
        return isActive(lockedBy, lockedAt, timeoutMinutes, LocalDateTime.now());
    }

    static boolean isActive(String lockedBy, LocalDateTime lockedAt, int timeoutMinutes, LocalDateTime now) {
        if (lockedBy == null || lockedAt == null) return false;
        return !expiresAt(lockedAt, timeoutMinutes).isBefore(now);
    }

    /** True when an active lock is held by someone other than {@code user} (a null user is never the holder). */
    public static boolean isHeldByOther(String lockedBy, LocalDateTime lockedAt, String user, int timeoutMinutes) {
        return isActive(lockedBy, lockedAt, timeoutMinutes) && !lockedBy.equals(user);
    }

    /** When the lock lapses; null when there is none. */
    public static LocalDateTime expiresAt(LocalDateTime lockedAt, int timeoutMinutes) {
        return lockedAt == null ? null : lockedAt.plusMinutes(timeoutMinutes);
    }
}
