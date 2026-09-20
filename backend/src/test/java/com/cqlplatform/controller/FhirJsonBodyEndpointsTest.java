package com.cqlplatform.controller;

import com.cqlplatform.model.CqlLibrary;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.service.cql.FhirLibraryService;
import com.cqlplatform.service.measure.FhirMeasureBundleImportService;
import com.cqlplatform.service.measure.FhirMeasureBundleImportService.BundleImportResult;
import com.cqlplatform.service.measure.FhirMeasureService;
import com.cqlplatform.service.measure.MeasureDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT-229 — the endpoints that take or return a raw FHIR resource. Spring Boot 4 converts HTTP
 * bodies with Jackson 3 ({@code tools.jackson}), while the FHIR services work on Jackson 2 trees
 * ({@code com.fasterxml.jackson.databind.JsonNode}). Binding a Jackson 2 {@code JsonNode} as a
 * {@code @RequestBody} therefore failed with 500 "Cannot construct instance of JsonNode" on every
 * import — found by smoke scenario 33, since no test went through the HTTP layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FhirJsonBodyEndpointsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BUNDLE = """
            {"resourceType":"Bundle","type":"collection","entry":[
              {"resource":{"resourceType":"Measure","name":"AdultCohort","version":"1.0.0"}}]}""";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FhirMeasureBundleImportService bundleImportService;
    @MockitoBean private FhirMeasureService fhirMeasureService;
    @MockitoBean private FhirLibraryService fhirLibraryService;
    @MockitoBean private MeasureDefinitionService definitionService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void importBundle_handsTheParsedTreeToTheService() throws Exception {
        when(bundleImportService.importBundle(any(JsonNode.class))).thenReturn(new BundleImportResult(
                MeasureDefinition.builder().id(9L).name("AdultCohort").version("1.0.0").status("draft").build(), 1, 2, 3, 2, 1, java.util.List.of("kept local copy")));

        mockMvc.perform(post("/api/measures/import/bundle").contentType(MediaType.APPLICATION_JSON).content(BUNDLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measure.id").value(9))
                .andExpect(jsonPath("$.librariesImported").value(1))
                .andExpect(jsonPath("$.librariesSkipped").value(2))
                .andExpect(jsonPath("$.valueSetsFound").value(3))
                .andExpect(jsonPath("$.valueSetsImported").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("kept local copy"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(bundleImportService).importBundle(body.capture());
        assertThat(body.getValue().path("entry").get(0).path("resource").path("name").asText()).isEqualTo("AdultCohort");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void importBundle_acceptsTheFhirJsonMediaType() throws Exception {
        when(bundleImportService.importBundle(any(JsonNode.class))).thenReturn(new BundleImportResult(
                MeasureDefinition.builder().id(9L).name("AdultCohort").version("1.0.0").build(), 0, 0, 0, 0, 0, java.util.List.of()));

        mockMvc.perform(post("/api/measures/import/bundle").contentType("application/fhir+json").content(BUNDLE))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void importFhirMeasure_handsTheParsedTreeToTheService() throws Exception {
        when(fhirMeasureService.importFhirMeasure(any(JsonNode.class)))
                .thenReturn(MeasureDefinition.builder().id(4L).name("AdultCohort").version("1.0.0").build());

        mockMvc.perform(post("/api/measures/import/fhir").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Measure\",\"name\":\"AdultCohort\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirMeasureService).importFhirMeasure(body.capture());
        assertThat(body.getValue().path("resourceType").asText()).isEqualTo("Measure");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void importFhirLibrary_handsTheParsedTreeToTheService() throws Exception {
        when(fhirLibraryService.importFhirLibrary(any(JsonNode.class)))
                .thenReturn(CqlLibrary.builder().id("lib-1").name("HospitalCommon").version("2.0.0").build());

        mockMvc.perform(post("/api/cql/libraries/import/fhir").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"Library\",\"name\":\"HospitalCommon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HospitalCommon"));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(fhirLibraryService).importFhirLibrary(body.capture());
        assertThat(body.getValue().path("name").asText()).isEqualTo("HospitalCommon");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void bodyThatIsNotJson_is400_notAnInternalError() throws Exception {
        mockMvc.perform(post("/api/measures/import/bundle").contentType(MediaType.APPLICATION_JSON)
                        .content("<Bundle xmlns=\"http://hl7.org/fhir\"/>"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/measures/import/fhir").contentType(MediaType.APPLICATION_JSON).content("{\"resourceType\":"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/cql/libraries/import/fhir").contentType(MediaType.APPLICATION_JSON).content("   "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bundleImportService, fhirLibraryService);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void exportFhirMeasure_returnsTheResourceItself() throws Exception {
        when(definitionService.getById(5L)).thenReturn(Optional.of(
                MeasureDefinition.builder().id(5L).name("AdultCohort").version("1.0.0").accessLevel("public").build()));
        ObjectNode measure = MAPPER.createObjectNode().put("resourceType", "Measure").put("name", "AdultCohort");
        measure.putArray("library").add("https://hospital.example.tw/fhir/Library/AdultCohortLogic|3.1.0");
        when(fhirMeasureService.exportAsFhirMeasure(5L)).thenReturn(measure);

        mockMvc.perform(get("/api/measures/5/fhir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Measure"))
                .andExpect(jsonPath("$.library[0]").value("https://hospital.example.tw/fhir/Library/AdultCohortLogic|3.1.0"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void exportFhirLibrary_returnsTheResourceItself() throws Exception {
        ObjectNode library = MAPPER.createObjectNode().put("resourceType", "Library").put("name", "HospitalCommon");
        library.putArray("content").addObject().put("contentType", "text/cql");
        when(fhirLibraryService.exportAsFhirLibrary("lib-1")).thenReturn(library);

        mockMvc.perform(get("/api/cql/libraries/lib-1/fhir"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Library"))
                .andExpect(jsonPath("$.content[0].contentType").value("text/cql"));
    }
}
