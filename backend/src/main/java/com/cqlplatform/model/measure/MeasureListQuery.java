package com.cqlplatform.model.measure;

import com.cqlplatform.exception.ValidationException;

import java.util.List;
import java.util.Locale;

/**
 * PAT-257: the server-side filters of the measure list page. {@code statuses} empty = any status; {@code scope}
 * narrows to the caller's own measures, the ones shared with them, or the public ones (the list's tabs, which
 * used to be filtered in the browser and so could not survive paging).
 */
public record MeasureListQuery(String search, String department, List<String> statuses, Scope scope, String username) {

    public enum Scope {
        ALL, MINE, SHARED, PUBLIC;

        public static Scope parse(String raw) {
            if (raw == null || raw.isBlank()) return ALL;
            try {
                return Scope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown scope: " + raw);
            }
        }
    }
}
