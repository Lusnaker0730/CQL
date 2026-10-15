package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * BUG-152 — {@link CqlExecutionService#executeInline} runs the CQL on the caller's thread and never
 * touches the execution pool. {@code MeasureEvaluationService}'s per-patient tasks already run on that
 * pool; the old fallback ({@code execute()}) submitted to it and waited, and once every pool thread
 * waited for a pool task the evaluation only progressed as the 120 s timeouts expired.
 */
@DisplayName("BUG-152 — executeInline stays off the execution pool")
class CqlExecutionServiceInlineTest {

    private static final String CQL = """
            library InlineProbe version '1.0.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1' called FHIRHelpers
            context Patient
            define "Encounters": Count([Encounter])
            define "Initial Population": exists [Encounter]
            """;

    @Test
    void executeInline_runsOnTheCallerThread_andNeverSubmitsToThePool() {
        ExecutorService poisonedPool = mock(ExecutorService.class, invocation -> {
            throw new AssertionError("executeInline must not use the execution pool: " + invocation.getMethod().getName());
        });
        CqlExecutionService service = new CqlExecutionService(
                new FhirDataProviderService(null, null, null),
                new CqlEngineFuzzTest.NoOpTerminologyService(),
                poisonedPool,
                Mockito.mock(CqlLibraryRepository.class));
        ReflectionTestUtils.setField(service, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(service, "maxRetrieveCount", 10000);
        ReflectionTestUtils.setField(service, "maxCollectionSize", 1000);
        ReflectionTestUtils.setField(service, "defaultFhirServerUrl", "http://localhost:9999/fhir");

        Patient patient = new Patient();
        patient.setId("p1");
        Encounter encounter = new Encounter();
        encounter.setId("e1");
        encounter.setSubject(new Reference("Patient/p1"));
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(CQL);
        request.setPatientId("p1");

        CqlExecutionResponse response = service.executeInline(request, new PrefetchRetrieveProvider(List.of(patient, encounter), "p1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResults().get("Encounters").getValue()).isEqualTo(1);
        assertThat(response.getResults().get("Initial Population").getValue()).isEqualTo(true);
    }
}
