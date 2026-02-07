package com.cqlplatform.controller;

import com.cqlplatform.model.measure.MeasureEvaluationResult;
import com.cqlplatform.service.measure.MeasureEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

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

    @MockBean
    private MeasureEvaluationService measureService;

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
}
