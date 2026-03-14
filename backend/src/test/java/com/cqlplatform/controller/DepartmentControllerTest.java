package com.cqlplatform.controller;

import com.cqlplatform.entity.DepartmentEntity;
import com.cqlplatform.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepartmentService departmentService;

    private DepartmentEntity createDept(String code, String name) {
        return DepartmentEntity.builder()
                .id(1L)
                .code(code)
                .name(name)
                .active(true)
                .build();
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getAll_shouldReturnDepartmentList() throws Exception {
        when(departmentService.getAll()).thenReturn(List.of(
                createDept("CARD", "Cardiology"),
                createDept("ONCO", "Oncology")
        ));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("CARD"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void create_shouldReturn201() throws Exception {
        DepartmentEntity created = createDept("NEURO", "Neurology");
        when(departmentService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NEURO\",\"name\":\"Neurology\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("NEURO"))
                .andExpect(jsonPath("$.name").value("Neurology"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void update_shouldReturn200() throws Exception {
        DepartmentEntity updated = createDept("CARD", "Cardiology Updated");
        when(departmentService.update(eq("CARD"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/departments/CARD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CARD\",\"name\":\"Cardiology Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cardiology Updated"));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getByCode_shouldReturnDepartment() throws Exception {
        when(departmentService.getByCode("CARD")).thenReturn(Optional.of(createDept("CARD", "Cardiology")));

        mockMvc.perform(get("/api/departments/CARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CARD"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getByCode_notFound_shouldReturn404() throws Exception {
        when(departmentService.getByCode("FAKE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/departments/FAKE"))
                .andExpect(status().isNotFound());
    }
}
