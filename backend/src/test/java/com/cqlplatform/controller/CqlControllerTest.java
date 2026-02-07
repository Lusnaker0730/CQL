package com.cqlplatform.controller;

import com.cqlplatform.model.*;
import com.cqlplatform.model.CqlTranslationResponse.TranslationMetadata;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.cql.CqlLibraryService;
import com.cqlplatform.service.cql.CqlTranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CqlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CqlTranslationService translationService;

    @MockBean
    private CqlExecutionService executionService;

    @MockBean
    private CqlLibraryService libraryService;

    @Test
    @WithMockUser
    void translate_validCql_shouldReturn200() throws Exception {
        CqlTranslationResponse response = CqlTranslationResponse.builder()
                .success(true)
                .elmJson("{}")
                .errors(List.of())
                .warnings(List.of())
                .metadata(TranslationMetadata.builder().libraryId("Test").libraryVersion("1.0").build())
                .build();
        when(translationService.translate(any())).thenReturn(response);

        mockMvc.perform(post("/api/cql/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void translate_emptyCql_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/cql/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void validate_shouldCallService() throws Exception {
        CqlTranslationResponse response = CqlTranslationResponse.builder()
                .success(true).errors(List.of()).warnings(List.of()).build();
        when(translationService.validate(any())).thenReturn(response);

        mockMvc.perform(post("/api/cql/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void execute_validRequest_shouldReturn200() throws Exception {
        CqlExecutionResponse response = CqlExecutionResponse.builder()
                .success(true)
                .results(new LinkedHashMap<>())
                .build();
        when(executionService.execute(any())).thenReturn(response);

        mockMvc.perform(post("/api/cql/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\",\"patientId\":\"p1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void listLibraries_shouldReturnList() throws Exception {
        CqlLibrary lib = CqlLibrary.builder()
                .id("Test-1.0").name("Test").version("1.0")
                .status("active").createdAt(LocalDateTime.now()).build();
        when(libraryService.getAllLibraries()).thenReturn(List.of(lib));

        mockMvc.perform(get("/api/cql/libraries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test"));
    }

    @Test
    @WithMockUser
    void listLibraries_withSearch_shouldFilter() throws Exception {
        when(libraryService.searchLibraries("Test")).thenReturn(List.of());

        mockMvc.perform(get("/api/cql/libraries").param("search", "Test"))
                .andExpect(status().isOk());

        verify(libraryService).searchLibraries("Test");
    }

    @Test
    @WithMockUser
    void getLibrary_existing_shouldReturn200() throws Exception {
        CqlLibrary lib = CqlLibrary.builder()
                .id("Test-1.0").name("Test").version("1.0").build();
        when(libraryService.getLibrary("Test-1.0")).thenReturn(Optional.of(lib));

        mockMvc.perform(get("/api/cql/libraries/Test-1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test"));
    }

    @Test
    @WithMockUser
    void getLibrary_notFound_shouldReturn404() throws Exception {
        when(libraryService.getLibrary("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/cql/libraries/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createLibrary_shouldReturn200() throws Exception {
        CqlLibrary lib = CqlLibrary.builder()
                .id("Test-1.0").name("Test").version("1.0").build();
        when(libraryService.saveLibrary(any(), any())).thenReturn(lib);

        mockMvc.perform(post("/api/cql/libraries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\",\"description\":\"desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test"));
    }

    @Test
    @WithMockUser
    void updateLibrary_shouldReturn200() throws Exception {
        CqlLibrary lib = CqlLibrary.builder()
                .id("Test-1.0").name("Test").version("1.0").build();
        when(libraryService.updateLibrary(eq("Test-1.0"), any(), any())).thenReturn(lib);

        mockMvc.perform(put("/api/cql/libraries/Test-1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void deleteLibrary_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/cql/libraries/Test-1.0"))
                .andExpect(status().isNoContent());

        verify(libraryService).deleteLibrary("Test-1.0");
    }

    @Test
    void translate_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/cql/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cql\":\"library Test version '1.0'\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listLibraries_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/cql/libraries"))
                .andExpect(status().isUnauthorized());
    }
}
