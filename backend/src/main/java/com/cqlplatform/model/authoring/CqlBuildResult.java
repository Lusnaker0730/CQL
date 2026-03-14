package com.cqlplatform.model.authoring;

import java.util.List;

public record CqlBuildResult(String cql, List<String> warnings) {

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
