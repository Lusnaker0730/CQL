package com.cqlplatform.model.fhir;

import java.util.List;

public record ResourceElementMetadata(
        String resourceType,
        List<ElementMetadata> elements
) {
}
