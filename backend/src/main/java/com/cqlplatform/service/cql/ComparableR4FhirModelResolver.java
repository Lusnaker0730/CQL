package com.cqlplatform.service.cql;

import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver;

/**
 * The platform's R4 model resolver.
 *
 * <p>Under cql-engine 4.x this class overrode {@code resolvePath} and {@code resolveType} to
 * work around two HAPI-reflection dispatch problems:
 * <ol>
 *   <li>{@code Encounter.class} — FHIR's {@code class} element collides with
 *       {@code Object.getClass()}; the 4.x resolver reflected on {@code getClass()} and returned
 *       the Java class object instead of {@code getClass_()}.</li>
 *   <li>BUG-106 — HAPI wraps every code element in {@code Enumeration<T>}; the 4.x resolver
 *       typed the value as the base code type, and FHIRHelpers 4.0.1's 251
 *       {@code ToString(SpecificEnum)} overloads then raised "Ambiguous call to operator
 *       ToString(CodeType)".</li>
 * </ol>
 *
 * <p>cql-engine 5.x removed both hooks. The engine no longer reflects on HAPI objects at all:
 * {@link R4FhirModelResolver#toCqlValue} converts a resource into a {@code ClassInstance} tree
 * using HAPI's runtime element definitions (so {@code class} is just the child named "class"),
 * and enumerations are typed by their FHIR type name through {@code enumFactoryTypeGetter}.
 * Both problems are therefore expected to be gone by construction — but "expected" is not
 * "verified": {@code CqlExecutionIntegrationTest} and {@code ModifierGeneratedCqlGoldenTest}
 * run {@code E.class} and {@code MedicationRequest.status} through FHIRHelpers against the real
 * engine and are the lock. If they ever fail with the messages above, the fix belongs in a
 * {@code toCqlValue} override here, not in the callers.
 */
public class ComparableR4FhirModelResolver extends R4FhirModelResolver {

    public ComparableR4FhirModelResolver() {
        super();
    }
}
