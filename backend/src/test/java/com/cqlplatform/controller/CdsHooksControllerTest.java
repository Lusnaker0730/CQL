package com.cqlplatform.controller;

import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.model.cds.CdsServiceDefinition;
import com.cqlplatform.service.cds.CdsHooksService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CdsHooksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CdsHooksService cdsHooksService;

    @Test
    void discovery_shouldReturnServicesWithoutAuth() throws Exception {
        List<CdsServiceDefinition> services = List.of(
                CdsServiceDefinition.builder()
                        .id("test-service").hook("patient-view").title("Test").build()
        );
        when(cdsHooksService.getSharedServiceDefinitions()).thenReturn(services);

        mockMvc.perform(get("/cds-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].id").value("test-service"));
    }

    @Test
    void invoke_shouldCallServiceWithoutAuth() throws Exception {
        CdsResponse response = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder()
                        .uuid(UUID.randomUUID().toString())
                        .summary("Test Card")
                        .indicator("info")
                        .source(CdsResponse.Source.builder().label("Test").build())
                        .build()))
                .build();
        when(cdsHooksService.invokeService(eq("test-service"), any())).thenReturn(response);

        mockMvc.perform(post("/cds-services/test-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hook\":\"patient-view\",\"hookInstance\":\"test-instance\",\"context\":{\"patientId\":\"p1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].summary").value("Test Card"));
    }

    @Test
    void feedback_shouldReturn200WithoutAuth() throws Exception {
        mockMvc.perform(post("/cds-services/test-service/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":[{\"card\":\"uuid\",\"outcome\":\"accepted\"}]}"))
                .andExpect(status().isOk());

        verify(cdsHooksService).processFeedback(eq("test-service"), any());
    }

    @Test
    void feedback_withOverride_shouldReturn200() throws Exception {
        mockMvc.perform(post("/cds-services/test-service/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":[{\"card\":\"uuid\",\"outcome\":\"overridden\",\"overrideReason\":{\"code\":\"reason\",\"display\":\"Clinical reason\"}}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void discovery_trailingSlash_shouldAlsoWork() throws Exception {
        when(cdsHooksService.getSharedServiceDefinitions()).thenReturn(List.of());

        mockMvc.perform(get("/cds-services/"))
                .andExpect(status().isOk());
    }

    @Test
    void sandbox_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(post("/cds-services/test-service/sandbox")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"test-service\",\"hook\":\"patient-view\",\"context\":{\"patientId\":\"p1\"},\"testData\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser
    void sandbox_withAuth_shouldReturn200() throws Exception {
        CdsResponse response = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder()
                        .uuid(UUID.randomUUID().toString())
                        .summary("Sandbox Card")
                        .indicator("info")
                        .source(CdsResponse.Source.builder().label("Test").build())
                        .build()))
                .build();
        when(cdsHooksService.invokeService(eq("test-service"), any())).thenReturn(response);

        mockMvc.perform(post("/cds-services/test-service/sandbox")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"test-service\",\"hook\":\"patient-view\",\"context\":{\"patientId\":\"p1\"},\"testData\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].summary").value("Sandbox Card"));
    }

    @Test
    void invoke_hookMismatch_shouldReturn400() throws Exception {
        when(cdsHooksService.invokeService(eq("test-service"), any()))
                .thenThrow(new IllegalArgumentException("Hook type mismatch"));

        mockMvc.perform(post("/cds-services/test-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hook\":\"order-select\",\"hookInstance\":\"test-instance\",\"context\":{\"patientId\":\"p1\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Hook type mismatch"));
    }
}
