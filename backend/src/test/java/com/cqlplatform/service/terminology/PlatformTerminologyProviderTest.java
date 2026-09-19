package com.cqlplatform.service.terminology;

import com.cqlplatform.model.terminology.PlatformValueSet;
import com.cqlplatform.model.terminology.PlatformValueSet.Concept;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.terminology.PlatformValueSetService.Resolved;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;
import org.opencds.cqf.cql.engine.terminology.ValueSetInfo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PAT-230 — how the CQL engine sees this installation's value sets during one evaluation. */
@ExtendWith(MockitoExtension.class)
class PlatformTerminologyProviderTest {

    private static final String NHI = "https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw";
    private static final String URL = "https://hospital.example.tw/fhir/ValueSet/HbA1cOrders";
    private static final long TENANT = 7L;

    @Mock private PlatformValueSetService valueSets;
    @Mock private TerminologyProvider delegate;

    private PlatformTerminologyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PlatformTerminologyProvider(TENANT, valueSets, delegate);
        lenient().when(valueSets.resolve(TENANT, URL, null)).thenReturn(Optional.of(new Resolved(
                PlatformValueSet.builder().id(1L).url(URL).version("1.0.0").status("active").concepts(List.of(
                        Concept.builder().system(NHI).code("09006C").display("HbA1c").build(),
                        Concept.builder().system(NHI).code("09139C").display("Glycated albumin").build())).build(), false)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ValueSetInfo ref(String url) {
        return new ValueSetInfo().withId(url);
    }

    private static Code code(String system, String code) {
        return new Code().withSystem(system).withCode(code);
    }

    @Test
    void membershipIsDecidedByThePlatformValueSet_withoutAskingAnyoneElse() {
        assertThat(provider.in(code(NHI, "09006C"), ref(URL))).isTrue();
        assertThat(provider.in(code(NHI, "99999Z"), ref(URL))).isFalse();
        assertThat(provider.in(code("http://loinc.org", "09006C"), ref(URL))).isFalse(); // same code, other system
        assertThat(provider.in(code(null, "09139C"), ref(URL))).isTrue(); // system-less code: same tolerance as the IG provider

        verify(delegate, never()).in(any(), any());
    }

    @Test
    void expandReturnsThePlatformCodes() {
        assertThat(provider.expand(ref(URL))).extracting(Code::getCode).containsExactly("09006C", "09139C");
        assertThat(provider.expand(ref(URL))).extracting(Code::getSystem).containsOnly(NHI);
    }

    @Test
    void aValueSetThePlatformDoesNotOwn_goesToTheExistingChain() {
        String vsac = "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001";
        when(valueSets.resolve(TENANT, vsac, null)).thenReturn(Optional.empty());
        when(delegate.in(any(), any())).thenReturn(true);

        assertThat(provider.in(code("http://snomed.info/sct", "44054006"), ref(vsac))).isTrue();
        verify(delegate).in(any(), any());
    }

    @Test
    void aPinnedVersionIsAskedForAsPinned() {
        when(valueSets.resolve(TENANT, URL, "2.0.0")).thenReturn(Optional.of(new Resolved(
                PlatformValueSet.builder().id(2L).url(URL).version("2.0.0").status("active").concepts(List.of(
                        Concept.builder().system(NHI).code("NEW").build())).build(), true)));

        assertThat(provider.in(code(NHI, "NEW"), new ValueSetInfo().withId(URL).withVersion("2.0.0"))).isTrue();
        assertThat(provider.in(code(NHI, "NEW"), ref(URL))).isFalse(); // unpinned still means 1.0.0
    }

    @Test
    void eachReferenceIsReadOncePerEvaluation() {
        for (int i = 0; i < 500; i++) provider.in(code(NHI, "09006C"), ref(URL));
        provider.expand(ref(URL));

        verify(valueSets, times(1)).resolve(TENANT, URL, null);
    }

    @Test
    void theTenantIsTheOneTheProviderWasCreatedFor_whateverThreadTheEngineCallsFrom() throws Exception {
        TenantContext.setCurrentTenantId(99L); // a different tenant on the calling thread
        assertThat(provider.in(code(NHI, "09006C"), ref(URL))).isTrue();

        // …and a worker thread that carries no tenant at all.
        PlatformTerminologyProvider fresh = new PlatformTerminologyProvider(TENANT, valueSets, delegate);
        assertThat(CompletableFuture.supplyAsync(() -> fresh.in(code(NHI, "09139C"), ref(URL))).get()).isTrue();

        verify(valueSets, never()).resolve(org.mockito.ArgumentMatchers.eq(99L), any(), any());
    }
}
