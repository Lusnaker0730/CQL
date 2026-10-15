package com.cqlplatform.controller;

import com.cqlplatform.exception.MeasureNotEvaluableException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.service.measure.MeasureDefinitionService;
import com.cqlplatform.service.measure.MeasureEvaluationService;
import com.cqlplatform.service.measure.TestCaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeasureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeasureEvaluationService measureService;

    @MockitoBean
    private MeasureDefinitionService definitionService;

    @MockitoBean
    private TestCaseService testCaseService;

    @MockitoBean
    private com.cqlplatform.service.ecqm.EcqmPublishService ecqmPublishService;

    @MockitoBean
    private com.cqlplatform.service.terminology.MeasureTerminologyService terminologyService;

    @MockitoBean
    private com.cqlplatform.service.measure.MeasurePackageService packageService;

    @MockitoBean
    private com.cqlplatform.service.measure.FhirMeasureBundleImportService bundleImportService;

    @Test
    @WithMockUser
    void evaluateMeasure_shouldReturn200() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .measureId("test-measure")
                .status("complete")
                .periodStart(LocalDate.of(2024, 1, 1))
                .periodEnd(LocalDate.of(2024, 12, 31))
                .groups(List.of())
                .build();
        when(measureService.evaluateMeasure(any())).thenReturn(result);

        mockMvc.perform(post("/api/measures/test-measure/$evaluate-measure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measureCql\":\"library T version '1.0'\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measureId").value("test-measure"))
                .andExpect(jsonPath("$.status").value("complete"));
    }

    @Test
    @WithMockUser
    void evaluateMeasure_withParams_shouldPassThrough() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .measureId("m1").status("complete").build();
        when(measureService.evaluateMeasure(any())).thenReturn(result);

        mockMvc.perform(post("/api/measures/m1/$evaluate-measure")
                        .param("subject", "patient-1")
                        .param("reportType", "summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    // ===== PAT-219: stored measure must be active to evaluate =====

    @Test
    @WithMockUser
    void evaluateMeasure_storedMeasureNotActive_shouldReturn409WithDistinctErrorLabel() throws Exception {
        MeasureDefinition draft = MeasureDefinition.builder()
                .id(7L).name("draft-measure").status("draft").scoringType("proportion")
                .cqlContent("library D version '1.0'").build();
        when(definitionService.getById(7L)).thenReturn(Optional.of(draft));
        when(measureService.evaluateMeasure(any(), eq(7L), any()))
                .thenThrow(new MeasureNotEvaluableException(7L, "draft"));

        mockMvc.perform(post("/api/measures/7/$evaluate-measure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Measure Not Evaluable"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("'draft'")));
    }

    @Test
    @WithMockUser
    void evaluateCustomMeasure_shouldReturn200() throws Exception {
        MeasureEvaluationResult result = MeasureEvaluationResult.builder()
                .measureId("custom").status("complete").build();
        when(measureService.evaluateMeasure(any())).thenReturn(result);

        mockMvc.perform(post("/api/measures/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measureId\":\"custom\",\"measureCql\":\"library C version '1.0'\"}"))
                .andExpect(status().isOk());
    }

    // ===== Test cases: tenant boundary (BUG-133) =====
    //
    // test_case.patient_bundle_json holds real $everything bundles imported from a clinic's
    // EHR, and test_case has no tenant_id — its tenant is its parent measure's. So the whole
    // boundary is the parent gate: definitionService.getById is tenant-scoped
    // (findByIdAndTenantId), and an empty Optional is exactly what a foreign tenant's measure
    // looks like to the caller.

    @Test
    @WithMockUser
    void listTestCases_whenMeasureNotInCallersTenant_shouldNotReadTestCases() throws Exception {
        when(definitionService.getById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/measures/42/test-cases"))
                .andExpect(status().isNotFound());

        // The gate must run first — no PHI is fetched for a measure the caller can't see.
        verify(testCaseService, never()).getTestCasesForMeasure(any());
    }

    @Test
    @WithMockUser
    void listTestCases_whenMeasureInCallersTenant_shouldReturnTestCases() throws Exception {
        when(definitionService.getById(7L))
                .thenReturn(Optional.of(MeasureDefinition.builder().id(7L).title("M").build()));
        when(testCaseService.getTestCasesForMeasure(7L))
                .thenReturn(List.of(TestCase.builder().id(1L).title("tc-1").build()));

        mockMvc.perform(get("/api/measures/7/test-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("tc-1"));
    }
    // ===== PAT-222: update carries the authenticated caller; status is not editable via PUT =====

    @Test
    @WithMockUser(username = "alice")
    void updateMeasure_shouldPassTheAuthenticatedCallerToTheService() throws Exception {
        MeasureDefinition owned = MeasureDefinition.builder()
                .id(7L).name("m").version("1.0.0").status("draft").scoringType("proportion")
                .ownerUsername("alice").build();
        when(definitionService.getById(7L)).thenReturn(Optional.of(owned));
        when(definitionService.update(eq(7L), any(), eq("alice"))).thenReturn(owned);

        mockMvc.perform(put("/api/measures/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m\",\"version\":\"1.0.0\",\"status\":\"draft\",\"scoringType\":\"proportion\"}"))
                .andExpect(status().isOk());

        verify(definitionService).update(eq(7L), any(), eq("alice"));
    }

    @Test
    @WithMockUser(username = "alice")
    void updateMeasure_statusChangeRefusedByService_shouldSurfaceAs400WithGuidance() throws Exception {
        MeasureDefinition owned = MeasureDefinition.builder()
                .id(7L).name("m").version("1.0.0").status("draft").scoringType("proportion")
                .ownerUsername("alice").build();
        when(definitionService.getById(7L)).thenReturn(Optional.of(owned));
        when(definitionService.update(eq(7L), any(), eq("alice")))
                .thenThrow(new ValidationException("Measure status cannot be changed through update (current: 'draft', "
                        + "requested: 'active'). Use the review workflow: submit-for-review, approve, reject or retire."));

        mockMvc.perform(put("/api/measures/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"m\",\"version\":\"1.0.0\",\"status\":\"active\",\"scoringType\":\"proportion\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("review workflow")));
    }

    // PAT-238: the measure page asks where a measure came from in the eCQM builder
    @Test
    @WithMockUser(username = "owner")
    void builderSource_returnsTheArtifactAndDrift_or204() throws Exception {
        MeasureDefinition m = MeasureDefinition.builder().id(5L).name("M").version("1.0.0")
                .ownerUsername("owner").accessLevel("public").build();
        when(definitionService.getById(5L)).thenReturn(Optional.of(m));
        when(ecqmPublishService.builderSourceOf(5L)).thenReturn(Optional.of(com.cqlplatform.model.ecqm.BuilderSource.builder()
                .artifactId(9L).artifactName("M").ownerUsername("owner")
                .measureEditedSincePublish(true).builderChangedSincePublish(false).build()));

        mockMvc.perform(get("/api/measures/5/builder-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactId").value(9))
                .andExpect(jsonPath("$.measureEditedSincePublish").value(true))
                .andExpect(jsonPath("$.builderChangedSincePublish").value(false));

        when(ecqmPublishService.builderSourceOf(5L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/measures/5/builder-source")).andExpect(status().isNoContent());
    }

    // ===== PAT-257 — the paged list: filters, scope, sort and paging as query params =====

    @Test
    @WithMockUser(username = "alice")
    void listMeasuresPage_passesFiltersScopeSortAndPaging() throws Exception {
        when(definitionService.listPage(any(), eq(1), eq(10), eq("name"), eq(false)))
                .thenReturn(com.cqlplatform.model.measure.MeasurePage.builder()
                        .content(List.of(MeasureDefinition.builder().id(7L).name("A").version("1.0.0")
                                .status("draft").scoringType("proportion").build()))
                        .page(1).size(10).totalElements(11).totalPages(2).build());

        mockMvc.perform(get("/api/measures/page")
                        .param("status", "draft,active").param("scope", "mine").param("sort", "name,asc")
                        .param("page", "1").param("size", "10").param("search", "hb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(7));

        org.mockito.ArgumentCaptor<com.cqlplatform.model.measure.MeasureListQuery> q =
                org.mockito.ArgumentCaptor.forClass(com.cqlplatform.model.measure.MeasureListQuery.class);
        verify(definitionService).listPage(q.capture(), eq(1), eq(10), eq("name"), eq(false));
        org.assertj.core.api.Assertions.assertThat(q.getValue().statuses()).containsExactly("draft", "active");
        org.assertj.core.api.Assertions.assertThat(q.getValue().scope()).isEqualTo(com.cqlplatform.model.measure.MeasureListQuery.Scope.MINE);
        org.assertj.core.api.Assertions.assertThat(q.getValue().search()).isEqualTo("hb");
        org.assertj.core.api.Assertions.assertThat(q.getValue().username()).isEqualTo("alice");
    }

    @Test
    @WithMockUser
    void listMeasuresPage_defaultsToNewestFirst_andRejectsABadDirectionOrScope() throws Exception {
        when(definitionService.listPage(any(), eq(0), eq(25), eq("updatedAt"), eq(true)))
                .thenReturn(com.cqlplatform.model.measure.MeasurePage.builder().content(List.of()).totalElements(0).build());
        mockMvc.perform(get("/api/measures/page")).andExpect(status().isOk());
        verify(definitionService).listPage(any(), eq(0), eq(25), eq("updatedAt"), eq(true));

        mockMvc.perform(get("/api/measures/page").param("sort", "name,sideways")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/measures/page").param("scope", "everyone")).andExpect(status().isBadRequest());
        verify(definitionService, times(1)).listPage(any(), anyInt(), anyInt(), any(), anyBoolean());
    }

    // ===== PAT-260 — terminology endpoints =====

    @Test
    @WithMockUser(username = "alice")
    void terminology_describePinAndUnpin_delegateToTheService() throws Exception {
        com.cqlplatform.model.measure.MeasureTerminology view = com.cqlplatform.model.measure.MeasureTerminology.builder()
                .measureId(5L).status("draft").pinnable(true)
                .items(List.of(com.cqlplatform.model.measure.MeasureTerminology.Item.builder()
                        .name("HbA1c").url("https://h/ValueSet/HbA1c").source("platform").resolvedVersion("1.0.0").pinned(false)
                        .versions(List.of()).build()))
                .build();
        when(terminologyService.describe(5L)).thenReturn(view);
        when(terminologyService.pin(eq(5L), any(), any(), any())).thenReturn(view);
        when(terminologyService.unpin(eq(5L), any(), any())).thenReturn(view);

        mockMvc.perform(get("/api/measures/5/terminology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinnable").value(true))
                .andExpect(jsonPath("$.items[0].url").value("https://h/ValueSet/HbA1c"));

        mockMvc.perform(post("/api/measures/5/terminology/pin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://h/ValueSet/HbA1c\",\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());
        verify(terminologyService).pin(5L, "https://h/ValueSet/HbA1c", "1.0.0", "alice");

        mockMvc.perform(post("/api/measures/5/terminology/pin")).andExpect(status().isOk());
        verify(terminologyService).pin(5L, null, null, "alice");

        mockMvc.perform(post("/api/measures/5/terminology/unpin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://h/ValueSet/HbA1c\"}")).andExpect(status().isOk());
        verify(terminologyService).unpin(5L, "https://h/ValueSet/HbA1c", "alice");
    }

    // ------------------------------------------------------------------ PAT-261 exchange package

    @Test
    @WithMockUser(username = "owner")
    void exportPackage_streamsTheZipUnderThePackageFileName() throws Exception {
        MeasureDefinition m = MeasureDefinition.builder().id(5L).name("M").version("1.0.0").ownerUsername("owner").build();
        when(definitionService.getById(5L)).thenReturn(Optional.of(m));
        when(packageService.exportPackage(5L)).thenReturn(new com.cqlplatform.service.measure.MeasurePackageService.MeasurePackage(
                "M-v1.0.0-FHIR", new byte[]{'P', 'K', 3, 4}, new com.cqlplatform.model.measure.MeasureExportConformance()));

        mockMvc.perform(get("/api/measures/5/export/package"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=M-v1.0.0-FHIR.zip"))
                .andExpect(content().bytes(new byte[]{'P', 'K', 3, 4}));
    }

    @Test
    @WithMockUser(username = "someone-else")
    void exportPackage_ofAPrivateMeasureOfAnotherUser_isForbidden() throws Exception {
        when(definitionService.getById(5L)).thenReturn(Optional.of(
                MeasureDefinition.builder().id(5L).name("M").ownerUsername("owner").accessLevel("private").build()));

        mockMvc.perform(get("/api/measures/5/export/package")).andExpect(status().isForbidden());
        verify(packageService, never()).exportPackage(any());
    }

    @Test
    @WithMockUser
    void importPackage_passesTheUploadAndAsVersionThrough() throws Exception {
        when(bundleImportService.importPackage(any(), eq("pkg.zip"), eq("2.0.0"))).thenReturn(
                new com.cqlplatform.service.measure.FhirMeasureBundleImportService.BundleImportResult(
                        MeasureDefinition.builder().id(9L).name("Imported").version("2.0.0").build(), 1, 0, 0, 0, 0, List.of()));

        mockMvc.perform(multipart("/api/measures/import/package")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "pkg.zip", "application/zip", new byte[]{'P', 'K', 3, 4}))
                        .param("asVersion", "2.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measure.name").value("Imported"))
                .andExpect(jsonPath("$.measure.version").value("2.0.0"))
                .andExpect(jsonPath("$.librariesImported").value(1));

        mockMvc.perform(multipart("/api/measures/import/package")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "empty.zip", "application/zip", new byte[0])))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ PAT-263 version diff

    @Test
    @WithMockUser(username = "reviewer")
    void versionCompare_isReadableBySharedUsers_andTheHtmlReportIsADocument() throws Exception {
        MeasureDefinition v1 = MeasureDefinition.builder().id(1L).name("M").version("1.0.0").ownerUsername("owner").accessLevel("shared").build();
        MeasureDefinition v2 = MeasureDefinition.builder().id(2L).name("M").version("1.1.0").ownerUsername("owner").accessLevel("shared").build();
        when(definitionService.getById(1L)).thenReturn(Optional.of(v1));
        when(definitionService.getById(2L)).thenReturn(Optional.of(v2));
        when(definitionService.compare(1L, 2L)).thenReturn(new com.cqlplatform.service.measure.MeasureDiffService().diff(
                MeasureDefinition.builder().id(1L).name("M").version("1.0.0").title("Old title").cqlContent("define A: 1").build(),
                MeasureDefinition.builder().id(2L).name("M").version("1.1.0").title("New title").cqlContent("define A: 2").build()));

        mockMvc.perform(get("/api/measures/version-compare").param("oldId", "1").param("newId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oldVersion").value("1.0.0"))
                .andExpect(jsonPath("$.cqlChanged").value(true))
                .andExpect(jsonPath("$.metadata[0].field").value("title"))
                .andExpect(jsonPath("$.metadata[0].kind").value("changed"))
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.metadataChanges[0]").value("Title changed: \"Old title\" → \"New title\""));

        mockMvc.perform(get("/api/measures/version-compare/human-readable").param("oldId", "1").param("newId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=measure-1-vs-2-changes.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<!DOCTYPE html>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"line added\"")));
    }
}
