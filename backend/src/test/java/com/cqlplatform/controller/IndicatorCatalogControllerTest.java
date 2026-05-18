package com.cqlplatform.controller;

import com.cqlplatform.entity.IndicatorCatalogEntity;
import com.cqlplatform.service.measure.IndicatorCatalogService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IndicatorCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndicatorCatalogService indicatorService;

    private IndicatorCatalogEntity createIndicator(String code, String name) {
        return IndicatorCatalogEntity.builder()
                .id(1L)
                .code(code)
                .name(name)
                .source("MOH")
                .category("Quality")
                .active(true)
                .build();
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void search_shouldReturnIndicators() throws Exception {
        when(indicatorService.search(null, null, null)).thenReturn(List.of(
                createIndicator("IND-001", "Indicator One"),
                createIndicator("IND-002", "Indicator Two")
        ));

        mockMvc.perform(get("/api/indicators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("IND-001"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getByCode_found_shouldReturnIndicator() throws Exception {
        when(indicatorService.getByCodeAndSource("IND-001", "MOH"))
                .thenReturn(Optional.of(createIndicator("IND-001", "Indicator One")));

        mockMvc.perform(get("/api/indicators/IND-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("IND-001"))
                .andExpect(jsonPath("$.name").value("Indicator One"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getByCode_notFound_shouldReturn404() throws Exception {
        when(indicatorService.getByCodeAndSource("FAKE", "MOH")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/indicators/FAKE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void create_shouldReturn201() throws Exception {
        IndicatorCatalogEntity created = createIndicator("IND-003", "New Indicator");
        when(indicatorService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/indicators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"IND-003\",\"name\":\"New Indicator\",\"source\":\"MOH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("IND-003"));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/indicators"))
                .andExpect(status().isUnauthorized());
    }
}
