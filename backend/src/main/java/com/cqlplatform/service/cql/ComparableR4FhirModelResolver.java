package com.cqlplatform.service.cql;

import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;

/**
 * Extended R4FhirModelResolver that handles edge cases in FHIR path resolution.
 *
 * Fixes:
 * 1. Encounter.class — Java reserved word conflict. HAPI FHIR stores the FHIR
 *    Encounter.class element via getClass_(), but the default model resolver
 *    calls Object.getClass() instead. We override to use getClass_().
 *
 * Note: We do NOT convert FHIR date/time types to CQL engine types here.
 * FHIRHelpers (auto-included by the CQL translator) already handles all
 * FHIR→CQL type conversions via .value accessors. Converting early in
 * resolvePath breaks the FHIRHelpers.ToDateTime() chain, causing temporal
 * operators like "during" to fail silently.
 */
public class ComparableR4FhirModelResolver extends R4FhirModelResolver {

    public ComparableR4FhirModelResolver() {
        super();
    }

    @Override
    public Object resolvePath(Object target, String path) {
        // FHIR Encounter.class conflicts with Java Object.getClass()
        // HAPI FHIR uses getClass_() for the FHIR element
        if ("class".equals(path) && target instanceof org.hl7.fhir.r4.model.Encounter encounter) {
            return encounter.getClass_();
        }

        return super.resolvePath(target, path);
    }
}
