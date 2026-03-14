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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private EcqmArtifactService artifactService;

    @MockBean
    private EcqmCqlGenerationService cqlGenerationService;

    @MockBean
    private EcqmPublishService publishService;

    @MockBean
    private EcqmExpressionTreeValidator validator;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private ModifierService modifierService;

    @MockBean
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
