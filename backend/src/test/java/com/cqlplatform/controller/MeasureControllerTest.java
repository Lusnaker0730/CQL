package com.cqlplatform.controller;

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
}
