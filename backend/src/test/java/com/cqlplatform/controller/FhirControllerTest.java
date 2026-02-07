package com.cqlplatform.controller;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
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
                        .param("params", "<script>alert(1)</script>"))
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
}
