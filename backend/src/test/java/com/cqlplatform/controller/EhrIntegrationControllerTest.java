package com.cqlplatform.controller;

import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.PatientImportEntity;
import com.cqlplatform.model.ehr.EhrConnectionRequest;
import com.cqlplatform.model.fhir.PatientSearchResult;
import com.cqlplatform.service.fhir.EhrConnectionService;
import com.cqlplatform.service.fhir.PatientImportService;
import com.cqlplatform.service.fhir.PatientSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EhrIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EhrConnectionService connectionService;

    @MockitoBean
    private PatientSearchService patientSearchService;

    @MockitoBean
    private PatientImportService patientImportService;

    private EhrConnectionEntity createConnection(Long id, String name) {
        EhrConnectionEntity entity = new EhrConnectionEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setFhirServerUrl("https://example.com/fhir/r4");
        entity.setAuthType("none");
        entity.setStatus("connected");
        return entity;
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void listConnections_shouldReturnList() throws Exception {
        when(connectionService.list(null)).thenReturn(List.of(
                createConnection(1L, "Hospital A"),
                createConnection(2L, "Hospital B")
        ));

        mockMvc.perform(get("/api/ehr/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Hospital A"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getConnection_shouldReturnConnection() throws Exception {
        when(connectionService.getById(1L)).thenReturn(createConnection(1L, "Hospital A"));

        mockMvc.perform(get("/api/ehr/connections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hospital A"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createConnection_shouldReturn201() throws Exception {
        EhrConnectionEntity created = createConnection(1L, "New Hospital");
        when(connectionService.create(any(EhrConnectionRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/ehr/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Hospital\",\"fhirServerUrl\":\"https://example.com/fhir/r4\",\"authType\":\"none\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Hospital"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void createConnection_withUserRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/ehr/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Hospital\",\"fhirServerUrl\":\"https://example.com/fhir/r4\",\"authType\":\"none\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void deleteConnection_withUserRole_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/ehr/connections/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void deleteConnection_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/ehr/connections/1"))
                .andExpect(status().isNoContent());

        verify(connectionService).delete(1L);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testConnection_shouldReturnUpdatedConnection() throws Exception {
        EhrConnectionEntity tested = createConnection(1L, "Hospital A");
        tested.setStatus("connected");
        when(connectionService.testConnection(1L)).thenReturn(tested);

        mockMvc.perform(post("/api/ehr/connections/1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("connected"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void testConnection_withUserRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/ehr/connections/1/test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void searchPatients_shouldReturnResults() throws Exception {
        when(patientSearchService.searchPatients(eq(1L), isNull(), isNull(), eq("Smith"), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/ehr/connections/1/patients?family=Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void searchPatients_withUserRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/ehr/connections/1/patients?family=Smith"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void listImports_shouldReturnImportHistory() throws Exception {
        when(patientImportService.listImports(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/ehr/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/ehr/connections"))
                .andExpect(status().isUnauthorized());
    }
}
