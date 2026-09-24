package com.cqlplatform.controller;

import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.model.authoring.FormTemplateCategory;
import com.cqlplatform.model.authoring.ModifierDefinition;
import com.cqlplatform.model.ecqm.*;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.authoring.ModifierService;
import com.cqlplatform.service.authoring.TemplateService;
import com.cqlplatform.service.ecqm.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcqmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EcqmArtifactService artifactService;

    @MockitoBean
    private EcqmCqlGenerationService cqlGenerationService;

    @MockitoBean
    private EcqmPublishService publishService;

    @MockitoBean
    private EcqmExpressionTreeValidator validator;

    @MockitoBean
    private TemplateService templateService;

    @MockitoBean
    private ModifierService modifierService;

    @MockitoBean
    private OwnershipVerifier ownershipVerifier;

    private EcqmArtifactResponse createArtifactResponse() {
        return EcqmArtifactResponse.builder()
                .id(1L)
                .name("Test eCQM")
                .version("1.0.0")
                .description("Test description")
                .status("draft")
                .fhirVersion("4.0.1")
                .scoringType("proportion")
                .populationBasis("boolean")
                .improvementNotation("increase")
                .ownerUsername("testuser")
                .populationGroups(new ArrayList<>())
                .supplementalData(new ArrayList<>())
                .stratifiers(new ArrayList<>())
                .baseElements(new ArrayList<>())
                .parameters(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ===== Authentication =====

    @Test
    void listArtifacts_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/ecqm/artifacts"))
                .andExpect(status().isUnauthorized());
    }

    // ===== List =====

    @Test
    @WithMockUser(username = "testuser")
    void listArtifacts_shouldReturnUserArtifacts() throws Exception {
        EcqmArtifactSummary summary = EcqmArtifactSummary.builder()
                .id(1L).name("Test eCQM").version("1.0.0").status("draft")
                .scoringType("proportion").populationBasis("boolean")
                .ownerUsername("testuser")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(artifactService.listByOwner("testuser")).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/ecqm/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test eCQM"))
                .andExpect(jsonPath("$[0].scoringType").value("proportion"));
    }

    // ===== Get =====

    @Test
    @WithMockUser(username = "testuser")
    void getArtifact_shouldReturnFull() throws Exception {
        EcqmArtifactResponse response = createArtifactResponse();
        when(artifactService.getById(1L)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/ecqm/artifacts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test eCQM"))
                .andExpect(jsonPath("$.scoringType").value("proportion"))
                .andExpect(jsonPath("$.populationBasis").value("boolean"));
    }

    // ===== Create =====

    @Test
    @WithMockUser(username = "testuser")
    void createArtifact_shouldReturn201() throws Exception {
        EcqmArtifactResponse response = createArtifactResponse();
        when(artifactService.create(any(EcqmArtifactRequest.class), eq("testuser"))).thenReturn(response);

        mockMvc.perform(post("/api/ecqm/artifacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test eCQM\", \"scoringType\": \"proportion\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test eCQM"));
    }

    // ===== Update =====

    @Test
    @WithMockUser(username = "testuser")
    void updateArtifact_shouldReturnUpdated() throws Exception {
        EcqmArtifactResponse response = createArtifactResponse();
        response.setDescription("Updated");
        when(artifactService.update(eq(1L), any(EcqmArtifactRequest.class), eq("testuser"))).thenReturn(response);

        mockMvc.perform(put("/api/ecqm/artifacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test eCQM\", \"description\": \"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    // PAT-236 — the workspace autosaves only changed keys, so the request must tell "not sent"
    // from "clear": through the real HTTP converter an absent date stays null, "" arrives as ""
    // (clear), an ISO date as itself. (An Optional would not do: absent arrives as
    // Optional.empty() too.) The response carries the metadata back as ISO dates / lists.
    @Test
    @WithMockUser(username = "testuser")
    void updateArtifact_standardMetadata_absentKeepsAndEmptyStringClears() throws Exception {
        EcqmArtifactResponse response = createArtifactResponse();
        response.setMeasureTypes(List.of("process"));
        response.setDefinitionTerms(List.of(java.util.Map.of("term", "HbA1c control", "definition", "< 7%")));
        response.setEffectiveStart(java.time.LocalDate.of(2026, 1, 1));
        response.setExperimental(Boolean.TRUE);
        org.mockito.ArgumentCaptor<EcqmArtifactRequest> captor = org.mockito.ArgumentCaptor.forClass(EcqmArtifactRequest.class);
        when(artifactService.update(eq(1L), captor.capture(), eq("testuser"))).thenReturn(response);

        mockMvc.perform(put("/api/ecqm/artifacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test eCQM\", \"measureTypes\": [\"process\", \"outcome\"],"
                                + " \"definitionTerms\": [{\"term\": \"HbA1c control\", \"definition\": \"< 7%\"}],"
                                + " \"effectiveStart\": \"2026-01-01\", \"effectiveEnd\": \"\", \"experimental\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measureTypes[0]").value("process"))
                .andExpect(jsonPath("$.definitionTerms[0].term").value("HbA1c control"))
                .andExpect(jsonPath("$.effectiveStart").value("2026-01-01"))
                .andExpect(jsonPath("$.experimental").value(true));

        EcqmArtifactRequest sent = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(sent.getMeasureTypes()).containsExactly("process", "outcome");
        org.assertj.core.api.Assertions.assertThat(sent.getDefinitionTerms()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(sent.getEffectiveStart()).isEqualTo("2026-01-01");
        org.assertj.core.api.Assertions.assertThat(sent.getEffectiveEnd()).isEmpty();      // "" → clear
        org.assertj.core.api.Assertions.assertThat(sent.getApprovalDate()).isNull();       // absent → keep
        org.assertj.core.api.Assertions.assertThat(sent.getLastReviewDate()).isNull();
        org.assertj.core.api.Assertions.assertThat(sent.getExperimental()).isTrue();
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateArtifact_unknownMeasureTypeOrMalformedDate_isRejected() throws Exception {
        mockMvc.perform(put("/api/ecqm/artifacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test eCQM\", \"measureTypes\": [\"efficiency\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/ecqm/artifacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test eCQM\", \"approvalDate\": \"01/02/2026\"}"))
                .andExpect(status().isBadRequest());
        verify(artifactService, never()).update(anyLong(), any(), anyString());
    }

    // ===== Delete =====

    @Test
    @WithMockUser(username = "testuser")
    void deleteArtifact_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/ecqm/artifacts/1"))
                .andExpect(status().isNoContent());

        verify(artifactService).delete(1L, "testuser");
    }

    // ===== Duplicate =====

    @Test
    @WithMockUser(username = "testuser")
    void duplicateArtifact_shouldReturnDuplicated() throws Exception {
        EcqmArtifactResponse response = createArtifactResponse();
        response.setName("Test eCQM (Copy)");
        when(artifactService.duplicate(1L, "testuser")).thenReturn(response);

        mockMvc.perform(post("/api/ecqm/artifacts/1/duplicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test eCQM (Copy)"));
    }

    // ===== Generate CQL =====

    @Test
    @WithMockUser(username = "testuser")
    void generateCql_shouldReturnCql() throws Exception {
        when(artifactService.getById(1L)).thenReturn(Optional.of(createArtifactResponse()));
        when(cqlGenerationService.generateCql(1L))
                .thenReturn(new CqlBuildResult("library Test version '1.0.0'\n", List.of()));

        mockMvc.perform(post("/api/ecqm/artifacts/1/cql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cql").value("library Test version '1.0.0'\n"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateCql_withWarnings_shouldReturnWarnings() throws Exception {
        when(artifactService.getById(1L)).thenReturn(Optional.of(createArtifactResponse()));
        when(cqlGenerationService.generateCql(1L))
                .thenReturn(new CqlBuildResult("library Test\n", List.of("Missing population")));

        mockMvc.perform(post("/api/ecqm/artifacts/1/cql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cql").exists())
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings[0]").value("Missing population"));
    }

    // ===== Publish =====

    @Test
    @WithMockUser(username = "testuser")
    void publish_shouldReturnResult() throws Exception {
        when(artifactService.getById(1L)).thenReturn(Optional.of(createArtifactResponse()));
        when(publishService.publish(1L, "testuser"))
                .thenReturn(PublishResult.builder()
                        .measureDefinitionId(100L)
                        .measureName("Test eCQM")
                        .cql("library Test\n")
                        .message("Published successfully")
                        .build());

        mockMvc.perform(post("/api/ecqm/artifacts/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measureDefinitionId").value(100))
                .andExpect(jsonPath("$.measureName").value("Test eCQM"));
    }

    // ===== Scoring types =====

    @Test
    @WithMockUser(username = "testuser")
    void getScoringTypes_shouldReturnConfig() throws Exception {
        mockMvc.perform(get("/api/ecqm/scoring-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoringTypes").isArray())
                .andExpect(jsonPath("$.populationBasisOptions").isArray())
                .andExpect(jsonPath("$.aggregateMethods").isArray());
    }

    // ===== Templates & Modifiers =====

    @Test
    @WithMockUser(username = "testuser")
    void getTemplates_shouldDelegate() throws Exception {
        when(templateService.getAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/ecqm/templates"))
                .andExpect(status().isOk());

        verify(templateService).getAllCategories();
    }

    @Test
    @WithMockUser(username = "testuser")
    void getModifiers_shouldDelegate() throws Exception {
        when(modifierService.getAllModifiers()).thenReturn(List.of());

        mockMvc.perform(get("/api/ecqm/modifiers"))
                .andExpect(status().isOk());

        verify(modifierService).getAllModifiers();
    }
}
