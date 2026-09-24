package com.cqlplatform.exception;

import lombok.Getter;

/**
 * PAT-238 — a re-publish from the eCQM builder would overwrite logic that was edited on the
 * measure page after the last publish (the measure's CQL / population mapping no longer hashes
 * to what the builder published). Mapped to HTTP 409 so the builder can ask the author and
 * retry with {@code force=true}; nothing is written.
 */
@Getter
public class PublishConflictException extends RuntimeException {

    private final Long measureId;

    public PublishConflictException(Long measureId) {
        super("Measure " + measureId + " was edited on the measure page after it was last published from the builder. "
                + "Publishing again would overwrite those edits with the builder's logic. "
                + "Re-publish with force=true to overwrite, or bring the edits into the builder first.");
        this.measureId = measureId;
    }
}
