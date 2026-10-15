package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IGetPage;
import ca.uhn.fhir.rest.gclient.IGetPageTyped;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ITransaction;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import com.cqlplatform.exception.FhirServerUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUG-151 (found by the PAT-266 load test) — {@link FhirDataProviderService#bulkFetchAllPatients}
 * must send the patient ids in batches (one request carrying 500 ids is a ~12 KB request line that
 * HAPI refuses with 400, so a clinic that size could not be evaluated at all) and must read every
 * page of every search (a server that clamps {@code _count} silently left resources out).
 */
@DisplayName("BUG-151 — bulk fetch batches the patient ids and follows every page")
class FhirDataProviderServiceBulkFetchTest {

    private FhirDataProviderService service;
    private IGenericClient client;
    private final List<String> searchUrls = new ArrayList<>();
    private final List<Bundle> patientBatches = new ArrayList<>();
    /** What a search URL answers, as the first page; pages are chained through {@link #nextPages}. */
    private Function<String, Bundle> searchAnswer = url -> new Bundle().setType(Bundle.BundleType.SEARCHSET);
    private final Map<Bundle, Bundle> nextPages = new java.util.IdentityHashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(IGenericClient.class);
        FhirClientFactory clientFactory = mock(FhirClientFactory.class);
        when(clientFactory.createClient(any())).thenReturn(client);
        service = new FhirDataProviderService(FhirContext.forR4(), clientFactory, CircuitBreakerRegistry.ofDefaults());
        ReflectionTestUtils.setField(service, "bulkFetchBatchSize", 100);
        ReflectionTestUtils.setField(service, "bulkFetchPageSize", 1000);

        IUntypedQuery<IBaseBundle> untyped = mock(IUntypedQuery.class);
        when(client.search()).thenReturn(untyped);
        when(untyped.byUrl(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            searchUrls.add(url);
            IQuery<IBaseBundle> query = mock(IQuery.class);
            IQuery<Bundle> typed = mock(IQuery.class);
            when(query.returnBundle(Bundle.class)).thenReturn(typed);
            when(typed.execute()).thenAnswer(i -> searchAnswer.apply(url));
            return query;
        });

        IGetPage loadPage = mock(IGetPage.class);
        when(client.loadPage()).thenReturn(loadPage);
        when(loadPage.next(any(Bundle.class))).thenAnswer(invocation -> {
            Bundle current = invocation.getArgument(0);
            IGetPageTyped<Bundle> typed = mock(IGetPageTyped.class);
            when(typed.execute()).thenReturn(nextPages.get(current));
            return typed;
        });

        ITransaction transaction = mock(ITransaction.class);
        when(client.transaction()).thenReturn(transaction);
        when(transaction.withBundle(any(Bundle.class))).thenAnswer(invocation -> {
            Bundle request = invocation.getArgument(0);
            patientBatches.add(request);
            ITransactionTyped<Bundle> typed = mock(ITransactionTyped.class);
            when(typed.execute()).thenReturn(patientResponse(request));
            return typed;
        });
    }

    /** The batch response HAPI gives for a batch of Patient GETs: one Patient per entry. */
    private static Bundle patientResponse(Bundle request) {
        Bundle response = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
        for (Bundle.BundleEntryComponent entry : request.getEntry()) {
            String id = entry.getRequest().getUrl().substring("Patient/".length());
            Patient patient = new Patient();
            patient.setId(id);
            response.addEntry().setResource(patient);
        }
        return response;
    }

    private static List<String> ids(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> "pat-20261015-" + i).toList();
    }

    private static Bundle page(Resource... resources) {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
        for (Resource r : resources) bundle.addEntry().setResource(r);
        return bundle;
    }

    private static Condition condition(String id, String patientId) {
        Condition c = new Condition();
        c.setId(id);
        c.setSubject(new Reference("Patient/" + patientId));
        return c;
    }

    private static Observation observation(String id, String patientId) {
        Observation o = new Observation();
        o.setId(id);
        o.setSubject(new Reference("Patient/" + patientId));
        return o;
    }

    @Test
    @DisplayName("250 patients → one search per resource type per batch of 100, each URL short, every patient fetched")
    void batchesTheIds() {
        List<String> patients = ids(250);
        Map<String, List<Resource>> result = service.bulkFetchAllPatients("http://fhir", patients, Set.of("Condition", "Observation", "Patient"));

        assertThat(searchUrls).hasSize(2 * 3);   // 2 types × 3 batches (100 + 100 + 50)
        for (String url : searchUrls) {
            int idsInUrl = url.split("Patient/").length - 1;
            assertThat(idsInUrl).isBetween(1, 100);
            assertThat(url.length()).isLessThan(4000);
            assertThat(url).endsWith("&_count=1000");
        }
        assertThat(searchUrls.stream().filter(u -> u.startsWith("Condition?subject=")).count()).isEqualTo(3);
        assertThat(searchUrls.stream().filter(u -> u.startsWith("Observation?patient=")).count()).isEqualTo(3);
        // the ids of the three Condition batches are the 250 ids, in order, without overlap
        List<String> conditionIds = searchUrls.stream().filter(u -> u.startsWith("Condition?"))
                .flatMap(u -> java.util.Arrays.stream(u.substring(u.indexOf('=') + 1, u.indexOf('&')).split(",")))
                .map(ref -> ref.substring("Patient/".length())).toList();
        assertThat(conditionIds).containsExactlyElementsOf(patients);

        assertThat(patientBatches).hasSize(3);
        assertThat(patientBatches.get(0).getEntry()).hasSize(100);
        assertThat(patientBatches.get(2).getEntry()).hasSize(50);
        assertThat(result).hasSize(250);
        for (String pid : patients) {
            assertThat(result.get(pid)).as(pid).hasSize(1);
            assertThat(result.get(pid).get(0)).isInstanceOf(Patient.class);
        }
    }

    @Test
    @DisplayName("a search with a next link is read to its last page and every page's resources reach their patient")
    void followsEveryPage() {
        Bundle page1 = page(condition("c1", "pat-20261015-1"), condition("c2", "pat-20261015-2"));
        page1.addLink().setRelation(Bundle.LINK_NEXT).setUrl("http://fhir?_getpages=abc&_getpagesoffset=1000");
        Bundle page2 = page(condition("c3", "pat-20261015-1"));
        page2.addLink().setRelation(Bundle.LINK_NEXT).setUrl("http://fhir?_getpages=abc&_getpagesoffset=2000");
        Bundle page3 = page(observation("o-ignored", "pat-20261015-2"), condition("c4", "pat-20261015-99"));   // 99 is not in the cohort
        nextPages.put(page1, page2);
        nextPages.put(page2, page3);
        searchAnswer = url -> page1;

        Map<String, List<Resource>> result = service.bulkFetchAllPatients("http://fhir", ids(2), Set.of("Condition"));

        assertThat(searchUrls).hasSize(1);
        assertThat(result.get("pat-20261015-1").stream().map(Resource::getIdPart)).containsExactly("c1", "c3", "pat-20261015-1");
        assertThat(result.get("pat-20261015-2").stream().map(Resource::getIdPart)).containsExactly("c2", "o-ignored", "pat-20261015-2");
        assertThat(result).doesNotContainKey("pat-20261015-99");
    }

    @Test
    @DisplayName("a failing search still fails loud — with the resource type in the message")
    void failsLoud() {
        searchAnswer = url -> {
            if (url.startsWith("Observation?")) throw new ca.uhn.fhir.rest.server.exceptions.InvalidRequestException("HTTP 400 Bad Request");
            return page();
        };
        assertThatThrownBy(() -> service.bulkFetchAllPatients("http://fhir", ids(3), Set.of("Observation")))
                .isInstanceOf(FhirServerUnavailableException.class)
                .hasMessageContaining("Observation");
    }

    @Test
    @DisplayName("an OperationOutcome error inside a 200 page is not data (PAT-112c)")
    void rejectsOperationOutcomeErrors() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("boom");
        searchAnswer = url -> page(outcome);
        assertThatThrownBy(() -> service.bulkFetchAllPatients("http://fhir", ids(1), Set.of("Condition")))
                .isInstanceOf(FhirServerUnavailableException.class);
    }

    @Test
    @DisplayName("batches() and bulkSearchUrl() — the shape of one request")
    void helpers() {
        assertThat(FhirDataProviderService.batches(ids(0), 100)).isEmpty();
        assertThat(FhirDataProviderService.batches(ids(100), 100)).hasSize(1);
        assertThat(FhirDataProviderService.batches(ids(101), 100)).hasSize(2);
        assertThat(FhirDataProviderService.batches(ids(5), 0)).hasSize(5);   // a zero batch size still makes progress
        assertThat(FhirDataProviderService.bulkSearchUrl("Condition", "subject", List.of("a", "b"), 500))
                .isEqualTo("Condition?subject=Patient/a,Patient/b&_count=500");
    }
}
