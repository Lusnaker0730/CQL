package com.cqlplatform.service.cql;

import org.hl7.fhir.r4.model.Enumeration;
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;

/**
 * Extended R4FhirModelResolver that fixes two HAPI dispatch issues.
 *
 * 1. Encounter.class — FHIR Encounter.class conflicts with Java Object.getClass().
 *    HAPI stores it as getClass_() but the default resolver reflects on getClass()
 *    and returns the Class object. Override resolvePath to call the correct getter.
 *
 * 2. Enumeration<T> dispatch (BUG-106) — HAPI wraps every FHIR code element in
 *    Enumeration<T> (e.g. MedicationRequest.status). The stock resolveType(Object)
 *    returns Enumeration.class, which the CQL dispatcher treats as the base code
 *    type. FHIRHelpers 4.0.1 exposes 251 ToString(SpecificEnum) overloads and every
 *    one of them matches a base code value, raising "Ambiguous call to operator
 *    ToString(CodeType)". Override resolveType to return the underlying Enum class
 *    (e.g. MedicationRequestStatus.class) so dispatch picks the correct overload
 *    uniquely.
 *
 *    We do NOT unwrap primitive wrappers (DecimalType, StringType, etc.) in
 *    resolvePath — FHIRHelpers handles those via .value accessors and early
 *    unwrapping breaks the chain ("Invalid path: value for type").
 */
public class ComparableR4FhirModelResolver extends R4FhirModelResolver {

    public ComparableR4FhirModelResolver() {
        super();
    }

    @Override
    public Object resolvePath(Object target, String path) {
        if ("class".equals(path) && target instanceof org.hl7.fhir.r4.model.Encounter encounter) {
            return encounter.getClass_();
        }
        return super.resolvePath(target, path);
    }

    @Override
    public Class<?> resolveType(Object value) {
        if (value instanceof Enumeration<?> enumeration) {
            Enum<?> actual = enumeration.getValue();
            if (actual != null) {
                return actual.getClass();
            }
        }
        return super.resolveType(value);
    }
}
