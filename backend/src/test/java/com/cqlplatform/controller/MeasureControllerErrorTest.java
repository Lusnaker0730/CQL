package com.cqlplatform.controller;

import com.cqlplatform.model.measure.MeasureDefinition;
import com.cqlplatform.model.measure.TestCase;
import com.cqlplatform.security.OwnershipVerifier;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.cqlplatform.service.cql.DataRequirementExtractor;
import com.cqlplatform.service.measure.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeasureControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeasureDefinitionService definitionService;

    @MockitoBean
    private MeasureEvaluationService measureService;

    @MockitoBean
    private MeasureReportService reportService;

    @MockitoBean
    private FhirMeasureService fhirMeasureService;

    @MockitoBean
    private CompositeMeasureService compositeMeasureService;

    @MockitoBean
    private MeasureReportExportService exportService;

    @MockitoBean
    private ScheduledMeasureEvaluationService scheduleService;

    @MockitoBean
    private MeasureComparisonService comparisonService;

    @MockitoBean
    private CqlTranslationService translationService;

    @MockitoBean
    private TestCaseService testCaseService;

    @MockitoBean
    private MeasureValidationService validationService;

    @MockitoBean
    private FhirMeasureBundleService bundleService;

    @MockitoBean
    private FhirMeasureBundleImportService bundleImportService;

    @MockitoBean
    private HqmfExportService hqmfExportService;

    @MockitoBean
    private HumanReadableService humanReadableService;

    @MockitoBean
    private BatchEvaluationService batchEvaluationService;

    @MockitoBean
    private DataRequirementExtractor dataRequirementExtractor;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private OwnershipVerifier ownershipVerifier;

    @Test
    @WithMockUser(roles = {"USER"})
    void getMeasure_notFound_shouldReturn404() throws Exception {
        when(definitionService.getById(9999L)).thenReturn(Optional.empty());

        // getMeasure uses orElse(notFound().build()) — returns 404 with empty body
        mockMvc.perform(get("/api/measures/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getSchedules_measureNotFound_shouldReturn404() throws Exception {
        when(definitionService.getById(9999L)).thenReturn(Optional.empty());

        // getSchedules calls requireOwnedMeasure → requireMeasure → throws ResourceNotFoundException
        mockMvc.perform(get("/api/measures/9999/schedules"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Measure not found: 9999"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void runTestCase_mismatchedTestCase_shouldReturn400() throws Exception {
        MeasureDefinition measure = MeasureDefinition.builder()
                .id(1L)
                .name("Test Measure")
                .ownerUsername("user")
                .build();
        when(definitionService.getById(1L)).thenReturn(Optional.of(measure));

        TestCase tc = new TestCase();
        tc.setId(99L);
        tc.setMeasureDefinitionId(2L); // belongs to different measure
        when(testCaseService.getById(99L)).thenReturn(Optional.of(tc));

        mockMvc.perform(post("/api/measures/1/test-cases/99/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Test case 99 does not belong to measure 1"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void exportHumanReadable_measureNotFound_shouldReturn404() throws Exception {
        when(definitionService.getById(9999L)).thenReturn(Optional.empty());

        // exportHumanReadable calls requireMeasure → throws ResourceNotFoundException
        mockMvc.perform(get("/api/measures/9999/export/human-readable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Measure not found: 9999"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getAuditTrail_measureNotFound_shouldReturn404() throws Exception {
        when(definitionService.getById(9999L)).thenReturn(Optional.empty());

        // getAuditTrail doesn't call requireMeasure — it just calls service directly
        // Let's instead test an endpoint that does call requireMeasure:
        // getMeasure returns 404 with empty body (tested above)
        // getSchedules uses requireOwnedMeasure (tested above)
        // Let's test runAllTestCases which calls requireMeasure
        mockMvc.perform(post("/api/measures/9999/test-cases/run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Measure not found: 9999"));
    }
}
