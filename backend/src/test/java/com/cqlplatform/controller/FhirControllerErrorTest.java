package com.cqlplatform.controller;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.service.fhir.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FhirControllerErrorTest {

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

    @MockBean
    private FhirStructureDefinitionService structureDefinitionService;

    @Test
    @WithMockUser(roles = {"USER"})
    void readResource_invalidResourceType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/InvalidType/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid FHIR resource type"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void readResource_invalidResourceId_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/Patient/bad!id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid resource ID"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void kickOffExport_invalidFhirServerUrl_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/fhir/$export?fhirServer=not-a-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid FHIR server URL"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void searchResources_invalidResourceType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/FakeResource"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid FHIR resource type: FakeResource"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getVsacValueSet_invalidOid_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/fhir/vsac/ValueSet/bad!oid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OID"));
    }
}
