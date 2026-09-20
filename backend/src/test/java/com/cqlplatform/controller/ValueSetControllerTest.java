package com.cqlplatform.controller;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.ValueSetRepository;
import com.cqlplatform.security.TenantContext;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT-230 — value set management over HTTP, and the three terminology endpoints every picker and
 * the Code Validation tab go through. The remote terminology service is mocked: a platform value
 * set must be answered without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "alice", roles = {"USER"})
class ValueSetControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NHI = "https://twcore.mohw.gov.tw/ig/twcore/CodeSystem/medical-service-payment-tw";
    private static final String URL = "https://hospital.example.tw/fhir/ValueSet/HbA1cOrders";
    private static final String BODY = """
            {"url":"%s","name":"HbA1cOrders","title":"HbA1c order codes","status":"active","ownerUsername":"mallory",
             "concepts":[{"system":"%s","code":"09006C","display":"HbA1c"},{"system":"%s","code":"09139C"}]}"""
            .formatted(URL, NHI, NHI);

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ValueSetRepository repository;
    @MockitoBean private FhirTerminologyService terminologyService;

    @BeforeEach
    void setUp() {
        // JwtAuthenticationFilter clears TenantContext at the end of every request, and @WithMockUser
        // carries no tenant claim — so these requests run as the `default` tenant, like any legacy
        // caller. Flyway is off under H2, so the row V57 seeds has to be created here.
        tenantRepository.findByCode("default").orElseGet(() -> tenantRepository.save(
                TenantEntity.builder().code("default").name("Default Tenant").active(true).build()));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private long create() throws Exception {
        String response = mockMvc.perform(post("/api/value-sets").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                // status and owner are the server's to decide, whatever the body says
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.ownerUsername").value("alice"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.conceptCount").value(2))
                .andExpect(jsonPath("$.sourceJson").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(response).path("id").asLong();
    }

    @Test
    void lifeCycleOverHttp() throws Exception {
        long id = create();

        mockMvc.perform(get("/api/value-sets").param("search", "hba1c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].concepts").doesNotExist()); // the list does not ship the codes
        mockMvc.perform(post("/api/value-sets/" + id + "/activate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(put("/api/value-sets/" + id).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/value-sets/" + id)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/value-sets/" + id + "/versions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.1.0\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("draft"));
        mockMvc.perform(post("/api/value-sets/" + id + "/versions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.1.0\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/value-sets/" + id + "/versions")).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/value-sets/999999")).andExpect(status().isNotFound());
    }

    @Test
    void invalidBody_is400_withEveryProblemListed() throws Exception {
        mockMvc.perform(post("/api/value-sets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"concepts\":[{\"code\":\"09006C\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem("name is required")));
    }

    @Test
    void fhirExportAndImport_goThroughTheRealMessageConverter() throws Exception {
        long id = create();
        String fhir = mockMvc.perform(get("/api/value-sets/" + id + "/fhir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("ValueSet"))
                .andExpect(jsonPath("$.compose.include[0].concept[1].code").value("09139C"))
                .andReturn().getResponse().getContentAsString();

        JsonNode asNextVersion = ((com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(fhir)).put("version", "2.0.0");
        mockMvc.perform(post("/api/value-sets/import/fhir").contentType(MediaType.APPLICATION_JSON).content(asNextVersion.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("imported"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.conceptCount").value(2));
        mockMvc.perform(post("/api/value-sets/import/fhir").contentType(MediaType.APPLICATION_JSON).content(asNextVersion.toString()))
                .andExpect(status().isConflict());
    }

    // ---- the endpoints the pickers, the expansion preview and the Code Validation tab use ----

    @Test
    void expand_answersAPlatformValueSet_withoutTheTerminologyServer() throws Exception {
        create();

        mockMvc.perform(get("/api/fhir/ValueSet/$expand").param("url", URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.expansion.total").value(2));
        mockMvc.perform(get("/api/fhir/ValueSet/$expand").param("url", URL).param("filter", "hba"))
                .andExpect(jsonPath("$.expansion.total").value(1))
                .andExpect(jsonPath("$.expansion.contains[0].code").value("09006C"));

        verify(terminologyService, never()).expandValueSet(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void search_listsPlatformValueSetsFirst_markedAsSuch() throws Exception {
        create();
        org.hl7.fhir.r4.model.ValueSet remote = new org.hl7.fhir.r4.model.ValueSet();
        remote.setUrl("http://example.org/fhir/ValueSet/remote-hba1c").setName("RemoteHbA1c").setTitle("Remote HbA1c");
        when(terminologyService.searchValueSets("hba1c")).thenReturn(List.of(remote));

        mockMvc.perform(get("/api/fhir/ValueSet").param("title", "hba1c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value(URL))
                .andExpect(jsonPath("$[0].source").value("platform"))
                .andExpect(jsonPath("$[0].status").value("draft"))
                .andExpect(jsonPath("$[1].source").value("remote"));
    }

    @Test
    void validateCode_acceptsTheUrlParameterTheFrontendSends_andChecksPlatformValueSetsLocally() throws Exception {
        create();

        mockMvc.perform(get("/api/fhir/CodeSystem/$validate-code").param("system", NHI).param("code", "09006C").param("url", URL))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true));
        mockMvc.perform(get("/api/fhir/CodeSystem/$validate-code").param("system", NHI).param("code", "NOPE").param("valueSet", URL))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(false));
        mockMvc.perform(get("/api/fhir/CodeSystem/$validate-code").param("system", NHI).param("code", "09006C"))
                .andExpect(status().isBadRequest());

        verify(terminologyService, never()).validateCode(anyString(), anyString(), anyString());
        org.assertj.core.api.Assertions.assertThat(repository.count()).isPositive();
    }
}
