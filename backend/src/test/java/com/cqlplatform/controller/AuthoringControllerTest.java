package com.cqlplatform.controller;

import com.cqlplatform.model.authoring.*;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.authoring.*;
import com.cqlplatform.service.cds.CdsHooksService;
import com.cqlplatform.service.cql.CqlLibraryService;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtifactService artifactService;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private ModifierService modifierService;

    @MockBean
    private CqlGenerationService cqlGenerationService;

    @MockBean
    private ExternalCqlLibraryService externalCqlLibraryService;

    @MockBean
    private ArtifactTestingService artifactTestingService;

    @MockBean
    private CdsHooksService cdsHooksService;

    @MockBean
    private CqlLibraryService cqlLibraryService;

    @MockBean
    private CqlImportService cqlImportService;

    @MockBean
    private QueryBuilderService queryBuilderService;

    @MockBean
    private TwcoreCatalogService twcoreCatalogService;

    @MockBean
    private OwnershipVerifier ownershipVerifier;

    private ArtifactResponse createArtifactResponse() {
        return ArtifactResponse.builder()
                .id(1L)
                .name("Test Artifact")
                .version("1.0.0")
                .description("Test description")
                .status("draft")
                .fhirVersion("R4")
                .ownerUsername("testuser")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void listArtifacts_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/authoring/artifacts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void listArtifacts_shouldReturnUserArtifacts() throws Exception {
        ArtifactSummary summary = ArtifactSummary.builder()
                .id(1L)
                .name("Test Artifact")
                .version("1.0.0")
                .status("draft")
                .ownerUsername("testuser")
                .build();

        when(artifactService.listByOwner("testuser")).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/authoring/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Artifact"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getArtifact_shouldReturnArtifact() throws Exception {
        ArtifactResponse response = createArtifactResponse();
        when(artifactService.getById(1L)).thenReturn(Optional.of(response));
        doNothing().when(ownershipVerifier).verifyOwnership(any());

        mockMvc.perform(get("/api/authoring/artifacts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Artifact"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createArtifact_shouldReturn201() throws Exception {
        ArtifactResponse response = createArtifactResponse();
        when(artifactService.create(any(ArtifactRequest.class), eq("testuser"))).thenReturn(response);

        mockMvc.perform(post("/api/authoring/artifacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Artifact\",\"version\":\"1.0.0\",\"status\":\"draft\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Artifact"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateArtifact_shouldReturnUpdated() throws Exception {
        ArtifactResponse response = createArtifactResponse();
        when(artifactService.update(eq(1L), any(ArtifactRequest.class), eq("testuser"))).thenReturn(response);

        mockMvc.perform(put("/api/authoring/artifacts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Artifact\",\"version\":\"1.0.0\",\"status\":\"draft\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Artifact"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteArtifact_shouldReturn204() throws Exception {
        doNothing().when(artifactService).delete(1L, "testuser");

        mockMvc.perform(delete("/api/authoring/artifacts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getTemplates_shouldReturnCategories() throws Exception {
        when(templateService.getAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/authoring/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getModifiers_noFilter_shouldReturnAll() throws Exception {
        when(modifierService.getAllModifiers()).thenReturn(List.of());

        mockMvc.perform(get("/api/authoring/modifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getModifiers_withInputType_shouldFilter() throws Exception {
        when(modifierService.getModifiersByInputType("boolean")).thenReturn(List.of());

        mockMvc.perform(get("/api/authoring/modifiers").param("inputType", "boolean"))
                .andExpect(status().isOk());

        verify(modifierService).getModifiersByInputType("boolean");
        verify(modifierService, never()).getAllModifiers();
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateCql_shouldReturnCql() throws Exception {
        ArtifactResponse response = createArtifactResponse();
        when(artifactService.getById(1L)).thenReturn(Optional.of(response));
        doNothing().when(ownershipVerifier).verifyOwnership(any());
        when(cqlGenerationService.generateCqlWithWarnings(eq(1L), any()))
                .thenReturn(new CqlBuildResult("library Test version '1.0'", List.of()));

        mockMvc.perform(post("/api/authoring/artifacts/1/cql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cql").value("library Test version '1.0'"))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateCql_withWarnings_shouldIncludeWarningsInResponse() throws Exception {
        ArtifactResponse response = createArtifactResponse();
        when(artifactService.getById(1L)).thenReturn(Optional.of(response));
        doNothing().when(ownershipVerifier).verifyOwnership(any());
        when(cqlGenerationService.generateCqlWithWarnings(eq(1L), any()))
                .thenReturn(new CqlBuildResult("library Test version '1.0'",
                        List.of("Unknown element type 'Foo' for element 'Bar'; defaulting to 'true'")));

        mockMvc.perform(post("/api/authoring/artifacts/1/cql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cql").value("library Test version '1.0'"))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings[0]").value("Unknown element type 'Foo' for element 'Bar'; defaulting to 'true'"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getQueryBuilderResources_shouldReturnResources() throws Exception {
        when(queryBuilderService.getResources()).thenReturn(List.of());

        mockMvc.perform(get("/api/authoring/query-builder/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
