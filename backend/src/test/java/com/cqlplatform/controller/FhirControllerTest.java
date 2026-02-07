package com.cqlplatform.controller;

import com.cqlplatform.service.fhir.*;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FhirControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FhirDataProviderService dataProviderService;

    @MockBean
    private FhirTerminologyService terminologyService;

    @MockBean
    private FhirBulkExportService bulkExportService;

    @MockBean
    private FhirValidationService validationService;

    @MockBean
    private VsacService vsacService;

    // ─── Existing Tests ──────────────────────────────────────────────

    @Test
    @WithMockUser
    void searchResources_validType_shouldReturn200() throws Exception {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        when(dataProviderService.searchResources(any(), eq("Patient"), any())).thenReturn(bundle);

        mockMvc.perform(get("/api/fhir/Patient"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void searchResources_invalidType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/InvalidResource"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void searchResources_invalidParams_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient")
                        .param("params", "name={invalid}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void searchResources_invalidUrl_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient")
                        .param("fhirServer", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void readResource_validIdAndType_shouldReturn200() throws Exception {
        Patient patient = new Patient();
        patient.setId("p1");
        patient.addName().setFamily("Test");
        when(dataProviderService.getResource(any(), eq("Patient"), eq("p1"))).thenReturn(patient);

        mockMvc.perform(get("/api/fhir/Patient/p1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void readResource_invalidType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/FakeType/p1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void readResource_invalidId_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient/../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void createResource_shouldReturn200() throws Exception {
        Patient patient = new Patient();
        patient.setId("new-p1");
        when(dataProviderService.createResource(any(), any())).thenReturn(patient);

        mockMvc.perform(post("/api/fhir/Patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Test\"}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void updateResource_shouldReturn200() throws Exception {
        Patient patient = new Patient();
        patient.setId("p1");
        when(dataProviderService.updateResource(any(), any())).thenReturn(patient);

        mockMvc.perform(put("/api/fhir/Patient/p1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Patient\",\"id\":\"p1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void deleteResource_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/fhir/Patient/p1"))
                .andExpect(status().isNoContent());

        verify(dataProviderService).deleteResource(any(), eq("Patient"), eq("p1"));
    }

    @Test
    @WithMockUser
    void expandValueSet_shouldReturn200() throws Exception {
        ValueSet vs = new ValueSet();
        vs.setUrl("http://hl7.org/fhir/ValueSet/test");
        when(terminologyService.expandValueSet(any(), any())).thenReturn(vs);

        mockMvc.perform(get("/api/fhir/ValueSet/$expand")
                        .param("url", "http://hl7.org/fhir/ValueSet/test"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void validateCode_shouldReturn200() throws Exception {
        when(terminologyService.validateCode(any(), any(), any())).thenReturn(true);

        mockMvc.perform(get("/api/fhir/CodeSystem/$validate-code")
                        .param("system", "http://loinc.org")
                        .param("code", "12345")
                        .param("valueSet", "http://test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true));
    }

    @Test
    void searchResources_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Subscription Test ───────────────────────────────────────────

    @Test
    @WithMockUser
    void createSubscription_shouldReturn200() throws Exception {
        Subscription subscription = new Subscription();
        subscription.setId("sub-1");
        when(dataProviderService.createResource(any(), any())).thenReturn(subscription);

        mockMvc.perform(post("/api/fhir/Subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Subscription\",\"status\":\"requested\",\"criteria\":\"Patient?name=test\",\"channel\":{\"type\":\"rest-hook\",\"endpoint\":\"http://example.com/hook\"}}"))
                .andExpect(status().isOk());
    }

    // ─── Validation Tests ────────────────────────────────────────────

    @Test
    @WithMockUser
    void validateResource_validResource_shouldReturn200() throws Exception {
        when(validationService.validateResource(any())).thenReturn(
                new FhirValidationService.ValidationResult(true, List.of()));

        mockMvc.perform(post("/api/fhir/$validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Patient\",\"name\":[{\"family\":\"Test\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @WithMockUser
    void validateResource_invalidResource_shouldReturnIssues() throws Exception {
        when(validationService.validateResource(any())).thenReturn(
                new FhirValidationService.ValidationResult(false, List.of(
                        new FhirValidationService.ValidationIssue("error", "Patient.gender", "Invalid gender value")
                )));

        mockMvc.perform(post("/api/fhir/$validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Patient\",\"gender\":\"invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].severity").value("error"));
    }

    // ─── Transaction Tests ───────────────────────────────────────────

    @Test
    @WithMockUser
    void executeTransaction_shouldReturn200() throws Exception {
        Bundle responseBundle = new Bundle();
        responseBundle.setType(Bundle.BundleType.TRANSACTIONRESPONSE);
        when(dataProviderService.executeTransaction(any(), any())).thenReturn(responseBundle);

        mockMvc.perform(post("/api/fhir/Bundle/$transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Bundle\",\"type\":\"transaction\",\"entry\":[]}"))
                .andExpect(status().isOk());
    }

    // ─── Patient Demographics Search Tests ───────────────────────────

    @Test
    @WithMockUser
    void searchPatientsByDemographics_shouldReturn200() throws Exception {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        when(dataProviderService.searchPatientsByDemographics(any(), any(), any(), any(), any())).thenReturn(bundle);

        mockMvc.perform(get("/api/fhir/Patient/$search-by-demographics")
                        .param("family", "Smith")
                        .param("given", "John"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void searchPatientsByDemographics_invalidDate_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient/$search-by-demographics")
                        .param("birthdate", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    // ─── VSAC Tests ──────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getVsacValueSet_validOid_shouldReturn200() throws Exception {
        ValueSet vs = new ValueSet();
        vs.setId("2.16.840.1.113883.3.464");
        vs.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464");
        when(vsacService.getValueSetByOid(any())).thenReturn(vs);

        mockMvc.perform(get("/api/fhir/vsac/ValueSet/2.16.840.1.113883.3.464"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getVsacValueSet_invalidOid_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/vsac/ValueSet/../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    // ─── Bulk Export Tests ───────────────────────────────────────────

    @Test
    @WithMockUser
    void kickOffExport_shouldReturn202() throws Exception {
        when(bulkExportService.kickOffExport(any(), any(), any(), any(), any())).thenReturn(
                new FhirBulkExportService.BulkExportKickOffResult("http://example.com/status", "system", "2024-01-01T00:00:00Z"));

        mockMvc.perform(post("/api/fhir/$export")
                        .param("fhirServer", "http://localhost:9999/fhir"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.exportType").value("system"));
    }

    // ─── Cache Management Tests ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void evictCache_withAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/fhir/cache/valueSets"))
                .andExpect(status().isOk());

        verify(terminologyService).evictCache("valueSets");
    }

    @Test
    @WithMockUser(roles = "USER")
    void evictCache_withoutAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/fhir/cache/valueSets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getCacheStats_shouldReturn200() throws Exception {
        when(terminologyService.getCacheStats()).thenReturn(Map.of(
                "valueSets", Map.of("size", 10L, "hitCount", 5L, "missCount", 3L, "hitRate", 0.625)
        ));

        mockMvc.perform(get("/api/fhir/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueSets.size").value(10));
    }
}
