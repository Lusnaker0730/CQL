package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAT-166: covers {@link FhirTerminologyService#tryLocalExpand} and the
 * local-IG fallback path in {@code expandValueSet}. Pure unit test — no
 * Spring context, no remote calls.
 */
class FhirTerminologyServiceTest {

    private static final String TW_CATEGORY_CS = "https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/category-code-tw";
    private static final String TW_CATEGORY_VS = "https://twcore.mohw.gov.tw/ig/twcore/ValueSet/category-code-tw";
    private static final String HL7_OBS_CAT_CS = "http://terminology.hl7.org/CodeSystem/observation-category";
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

    private FhirImplementationGuideService igService;
    private FhirTerminologyService terminologyService;
    private Map<String, ValueSet> valueSetsByUrl;
    private Map<String, CodeSystem> codeSystemsByUrl;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        FhirContext fhirContext = FhirContext.forR4();
        igService = new FhirImplementationGuideService(fhirContext);
        // Mark the IG as loaded so terminologyService treats it as authoritative.
        injectIgMetadata();

        Field vsField = FhirImplementationGuideService.class.getDeclaredField("valueSetsByUrl");
        vsField.setAccessible(true);
        valueSetsByUrl = (Map<String, ValueSet>) vsField.get(igService);

        Field csField = FhirImplementationGuideService.class.getDeclaredField("codeSystemsByUrl");
        csField.setAccessible(true);
        codeSystemsByUrl = (Map<String, CodeSystem>) csField.get(igService);

        terminologyService = new FhirTerminologyService(fhirContext, new NoOpCacheManager(), igService, null);
    }

    private void injectIgMetadata() throws Exception {
        Field metaField = FhirImplementationGuideService.class.getDeclaredField("packageMetadata");
        metaField.setAccessible(true);
        metaField.set(igService, new FhirImplementationGuideService.PackageMetadata(
                "tw.gov.mohw.twcore", "1.0.0", "TW Core IG",
                "https://twcore.mohw.gov.tw/ig/twcore", "4.0.1", 0, 0, 0));
        Field vsField = FhirImplementationGuideService.class.getDeclaredField("validationSupport");
        vsField.setAccessible(true);
        // Non-null sentinel — isLoaded() only checks for null-ness.
        vsField.set(igService, new org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport(
                FhirContext.forR4()));
    }

    // --- tryLocalExpand: pure concept enumeration ---

    @Test
    void tryLocalExpand_pureConceptCompose_returnsEnumeratedExpansion() {
        ValueSet vs = new ValueSet();
        vs.setUrl("urn:test:enum");
        vs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        ValueSet.ConceptSetComponent include = vs.getCompose().addInclude();
        include.setSystem(SNOMED_SYSTEM);
        include.addConcept().setCode("160245001").setDisplay("No current problems");
        include.addConcept().setCode("12345").setDisplay("Test finding");

        ValueSet expanded = terminologyService.tryLocalExpand(vs, null);

        assertNotNull(expanded);
        assertTrue(expanded.hasExpansion());
        assertEquals(2, expanded.getExpansion().getContains().size());
        assertEquals(2, expanded.getExpansion().getTotal());
        assertEquals("160245001", expanded.getExpansion().getContains().get(0).getCode());
        assertEquals(SNOMED_SYSTEM, expanded.getExpansion().getContains().get(0).getSystem());
    }

    @Test
    void tryLocalExpand_withTextFilter_keepsMatchingCodesOnly() {
        ValueSet vs = new ValueSet();
        ValueSet.ConceptSetComponent include = vs.getCompose().addInclude();
        include.setSystem(SNOMED_SYSTEM);
        include.addConcept().setCode("160245001").setDisplay("No current problems");
        include.addConcept().setCode("12345").setDisplay("Diabetes mellitus");

        ValueSet expanded = terminologyService.tryLocalExpand(vs, "diabetes");

        assertNotNull(expanded);
        assertEquals(1, expanded.getExpansion().getContains().size());
        assertEquals("12345", expanded.getExpansion().getContains().get(0).getCode());
    }

    // --- tryLocalExpand: include CodeSystem (no inline concepts) ---

    @Test
    void tryLocalExpand_includeWholeLocalCodeSystem_enumeratesAllCodes() {
        // Bundle a local CodeSystem with a tiny concept tree
        CodeSystem cs = new CodeSystem();
        cs.setUrl(TW_CATEGORY_CS);
        cs.addConcept().setCode("vital-signs").setDisplay("Vital Signs");
        CodeSystem.ConceptDefinitionComponent parent = cs.addConcept().setCode("laboratory").setDisplay("Laboratory");
        parent.addConcept().setCode("chemistry").setDisplay("Chemistry");
        codeSystemsByUrl.put(TW_CATEGORY_CS, cs);

        ValueSet vs = new ValueSet();
        vs.setUrl(TW_CATEGORY_VS);
        vs.getCompose().addInclude().setSystem(TW_CATEGORY_CS);   // no inline concepts → "all"

        ValueSet expanded = terminologyService.tryLocalExpand(vs, null);

        assertNotNull(expanded);
        // Tree traversal flattens: vital-signs + laboratory + chemistry = 3
        assertEquals(3, expanded.getExpansion().getContains().size());
        assertTrue(expanded.getExpansion().getContains().stream()
                .anyMatch(c -> "chemistry".equals(c.getCode())));
    }

    // --- tryLocalExpand: bail-out paths (returns null → caller returns VS body) ---

    @Test
    void tryLocalExpand_filterBasedCompose_returnsNull() {
        // SNOMED is-a hierarchy — only a SCT-aware terminology server can enumerate
        ValueSet vs = new ValueSet();
        ValueSet.ConceptSetComponent include = vs.getCompose().addInclude();
        include.setSystem(SNOMED_SYSTEM);
        include.addFilter().setProperty("concept")
                .setOp(ValueSet.FilterOperator.ISA).setValue("404684003");

        assertNull(terminologyService.tryLocalExpand(vs, null));
    }

    @Test
    void tryLocalExpand_chainedValueSetReference_returnsNull() {
        ValueSet vs = new ValueSet();
        vs.getCompose().addInclude().addValueSet("urn:test:other-vs");

        assertNull(terminologyService.tryLocalExpand(vs, null));
    }

    @Test
    void tryLocalExpand_includeCodeSystemNotInLocalIg_returnsNull() {
        // category-code-tw's compose references HL7's observation-category CS,
        // which is NOT in our bundle. We can resolve the TW one but not the
        // HL7 one → bail out so caller returns the VS body instead.
        ValueSet vs = new ValueSet();
        vs.getCompose().addInclude().setSystem(HL7_OBS_CAT_CS);

        assertNull(terminologyService.tryLocalExpand(vs, null));
    }

    @Test
    void tryLocalExpand_noCompose_returnsNull() {
        assertNull(terminologyService.tryLocalExpand(new ValueSet(), null));
    }

    // --- expandValueSet path-selection (via valueSetsByUrl map) ---

    @Test
    void expandValueSet_localVsWithPureCompose_skipsRemoteAndExpands() {
        ValueSet vs = new ValueSet();
        vs.setUrl("urn:test:local");
        vs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        vs.getCompose().addInclude().setSystem(SNOMED_SYSTEM)
                .addConcept().setCode("160245001").setDisplay("No problem");
        valueSetsByUrl.put("urn:test:local", vs);

        // No remote call needed — if the implementation reaches tx.fhir.org
        // this would either fail or hang depending on network. The fact that
        // it returns a real expansion proves the local path was taken.
        ValueSet result = terminologyService.expandValueSet("urn:test:local", null);

        assertNotNull(result);
        assertTrue(result.hasExpansion());
        assertEquals(1, result.getExpansion().getContains().size());
    }

    @Test
    void expandValueSet_localVsWithFilterCompose_returnsDefinitionBody() {
        // condition-code-sct-tw shape: SNOMED is-a filter that local can't enumerate
        ValueSet vs = new ValueSet();
        vs.setUrl(TW_CATEGORY_VS);
        vs.setName("TestVS");
        vs.getCompose().addInclude().setSystem(SNOMED_SYSTEM)
                .addFilter().setProperty("concept")
                .setOp(ValueSet.FilterOperator.ISA).setValue("404684003");
        valueSetsByUrl.put(TW_CATEGORY_VS, vs);

        ValueSet result = terminologyService.expandValueSet(TW_CATEGORY_VS, null);

        // Got the definition body (no synthetic expansion attached), no 503.
        assertNotNull(result);
        assertEquals(TW_CATEGORY_VS, result.getUrl());
        assertTrue(result.hasCompose());
        assertFalse(result.hasExpansion());
    }

    // --- BUG-124: $lookup not-found classification + negative result ---

    @Test
    void isClientError_4xxStatuses_returnTrue() {
        // 404 not found and 400 invalid request both mean "the server understood
        // and rejected the code/request" — a negative result, not an outage.
        assertTrue(FhirTerminologyService.isClientError(
                new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("not found"))); // 404
        assertTrue(FhirTerminologyService.isClientError(
                new ca.uhn.fhir.rest.server.exceptions.InvalidRequestException("bad request"))); // 400
    }

    @Test
    void isClientError_5xxAndConnectionFailures_returnFalse() {
        // 500 from upstream and a connection failure (status 0, no HTTP response)
        // are both genuine outages → must NOT be classified as not-found.
        assertFalse(FhirTerminologyService.isClientError(
                new ca.uhn.fhir.rest.server.exceptions.InternalErrorException("boom")));           // 500
        assertFalse(FhirTerminologyService.isClientError(
                new ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException("conn")));    // 0
    }

    @Test
    void codeLookupResult_notFound_hasFoundFalseAndNoData() {
        FhirTerminologyService.CodeLookupResult r =
                FhirTerminologyService.CodeLookupResult.notFound(SNOMED_SYSTEM, "99999");
        assertFalse(r.found());
        assertEquals(SNOMED_SYSTEM, r.system());
        assertEquals("99999", r.code());
        assertNull(r.display());
        assertTrue(r.designations().isEmpty());
    }

    @Test
    void lookupCode_foundInLocalIg_returnsFoundResult() {
        // Local IG hit must never touch the remote server and must report found=true.
        CodeSystem cs = new CodeSystem();
        cs.setUrl(SNOMED_SYSTEM);
        cs.setName("SNOMED CT");
        cs.addConcept().setCode("160245001").setDisplay("No current problems");
        codeSystemsByUrl.put(SNOMED_SYSTEM, cs);

        FhirTerminologyService.CodeLookupResult r =
                terminologyService.lookupCode(SNOMED_SYSTEM, "160245001");

        assertTrue(r.found());
        assertEquals("No current problems", r.display());
    }
}
