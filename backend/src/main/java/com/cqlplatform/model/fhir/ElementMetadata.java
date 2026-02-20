package com.cqlplatform.model.fhir;

import java.util.List;

public record ElementMetadata(
        String name,
        String path,
        String type,
        boolean isArray,
        boolean isRequired,
        int min,
        String max,
        boolean isChoiceType,
        List<String> choiceTypes,
        String bindingStrength,
        String bindingValueSetUrl,
        String bindingCodeSystemUrl,
        List<String> boundCodes,
        List<ElementMetadata> children,
        String description,
        List<String> referenceTargets
) {
}
