package com.cqlplatform.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAT-253 — a write to a measure, test case or CQL library that someone else holds an active
 * edit lock on (or an unlock by someone who is neither the holder nor the owner). Mapped to
 * HTTP 409 {@code Locked}; {@code details} carries the holder and the expiry so a client can
 * show "locked by X until Y" without parsing the message.
 */
@Getter
public class ResourceLockedException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;
    private final String lockedBy;
    private final LocalDateTime lockExpiresAt;

    public ResourceLockedException(String resourceType, Object resourceId, String lockedBy, LocalDateTime lockExpiresAt) {
        this(resourceType, resourceId, lockedBy, lockExpiresAt,
                resourceType + " " + resourceId + " is locked by " + lockedBy
                        + (lockExpiresAt != null ? " (lock expires at " + lockExpiresAt + ")" : "") + ".");
    }

    public ResourceLockedException(String resourceType, Object resourceId, String lockedBy, LocalDateTime lockExpiresAt,
                                   String message) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = String.valueOf(resourceId);
        this.lockedBy = lockedBy;
        this.lockExpiresAt = lockExpiresAt;
    }

    public List<String> getDetails() {
        return lockExpiresAt != null
                ? List.of("lockedBy: " + lockedBy, "lockExpiresAt: " + lockExpiresAt)
                : List.of("lockedBy: " + lockedBy);
    }
}
