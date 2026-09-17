package com.cqlplatform.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.repository.PatientImportRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.measure.TestCaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PAT-206 — uploaded FHIR bundle import (e.g. a 健康存摺 / My Health Bank export).
 * The upload path shares its landing with the EHR path but has no connection: the row is
 * tenant-scoped, connection_id is null, and source is 'fhir-upload'.
 */
@ExtendWith(MockitoExtension.class)
class PatientImportServiceTest {

    @Mock private EhrConnectionService connectionService;
    @Mock private FhirClientFactory fhirClientFactory;
    @Mock private PatientImportRepository importRepository;
    @Mock private TestCaseService testCaseService;
    @Mock private TenantRepository tenantRepository;

    private PatientImportService service;

    private static final Long TENANT = 7L;

    @BeforeEach
    void setUp() {
        // Real FhirContext so the parser actually validates the uploaded JSON.
        service = new PatientImportService(connectionService, fhirClientFactory, FhirContext.forR4(),
                importRepository, testCaseService, tenantRepository);
        TenantContext.setCurrentTenantId(TENANT);
        lenient().when(importRepository.save(any(PatientImportEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static final String PATIENT_BUNDLE = """
        {"resourceType":"Bundle","type":"collection","entry":[
          {"resource":{"resourceType":"Patient","id":"p-123",
            "name":[{"family":"陳","given":["小明"]}],
            "identifier":[{"value":"A123456789"}]}},
          {"resource":{"resourceType":"Condition","id":"c-1",
            "code":{"coding":[{"system":"https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/icd-10-cm-2023-tw","code":"E11"}]}}}
        ]}""";

    @Test
    void importUploadedBundle_shouldSaveTenantScopedRowWithUploadSourceAndNullConnection() {
        PatientImportEntity result = service.importUploadedBundle(PATIENT_BUNDLE, null);

        ArgumentCaptor<PatientImportEntity> saved = ArgumentCaptor.forClass(PatientImportEntity.class);
        verify(importRepository).save(saved.capture());
        PatientImportEntity row = saved.getValue();
        assertThat(row.getSource()).isEqualTo("fhir-upload");
        assertThat(row.getConnectionId()).isNull();
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getPatientFhirId()).isEqualTo("p-123");
        assertThat(row.getResourceCount()).isEqualTo(2);
        assertThat(result).isNotNull();
        // No measure → no test case, and the EHR connection path is never touched.
        verifyNoInteractions(connectionService, fhirClientFactory, testCaseService);
    }

    @Test
    void importUploadedBundle_withMeasureId_shouldAlsoCreateTestCase() {
        when(testCaseService.create(eq(42L), any(TestCase.class)))
                .thenReturn(TestCase.builder().id(99L).build());

        service.importUploadedBundle(PATIENT_BUNDLE, 42L);

        ArgumentCaptor<TestCase> tc = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseService).create(eq(42L), tc.capture());
        assertThat(tc.getValue().getPatientBundleJson()).contains("A123456789");
    }

    @Test
    void importUploadedBundle_invalidJson_shouldThrowValidation() {
        assertThatThrownBy(() -> service.importUploadedBundle("not a bundle", null))
                .isInstanceOf(ValidationException.class);
        verify(importRepository, never()).save(any());
    }

    @Test
    void importUploadedBundle_emptyBundle_shouldThrowValidation() {
        assertThatThrownBy(() ->
                service.importUploadedBundle("{\"resourceType\":\"Bundle\",\"type\":\"collection\"}", null))
                .isInstanceOf(ValidationException.class);
        verify(importRepository, never()).save(any());
    }

    @Test
    void importUploadedBundle_tooManyEntries_shouldThrowValidationAndNotSave() {
        // PAT-206 follow-up: bound the work a single upload can trigger (cap is 10,000).
        StringBuilder sb = new StringBuilder("{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[");
        for (int i = 0; i < 10_001; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"p").append(i).append("\"}}");
        }
        sb.append("]}");

        assertThatThrownBy(() -> service.importUploadedBundle(sb.toString(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too many entries");
        verify(importRepository, never()).save(any());
    }
}
