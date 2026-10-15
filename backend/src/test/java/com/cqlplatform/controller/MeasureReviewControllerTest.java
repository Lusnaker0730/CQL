package com.cqlplatform.controller;

import com.cqlplatform.model.measure.MeasureReview;
import com.cqlplatform.service.measure.MeasureReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PAT-262: the review endpoints hand the caller and the body through; bad bodies are 400. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeasureReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MeasureReviewService reviewService;

    private static MeasureReview review() {
        return MeasureReview.builder().measureId(5L).status("in-review")
                .reviewers(List.of(MeasureReview.Reviewer.builder().username("bob").decision("pending").build()))
                .comments(List.of())
                .summary(MeasureReview.Summary.builder().reviewers(1).pending(1).build())
                .canAssign(true).canComment(true).reviewer(false).build();
    }

    @Test
    @WithMockUser(username = "alice")
    void getReview_returnsReviewersCommentsAndFlags() throws Exception {
        when(reviewService.getReview(5L, "alice")).thenReturn(review());

        mockMvc.perform(get("/api/measures/5/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measureId").value(5))
                .andExpect(jsonPath("$.reviewers[0].username").value("bob"))
                .andExpect(jsonPath("$.summary.pending").value(1))
                .andExpect(jsonPath("$.canAssign").value(true))
                .andExpect(jsonPath("$.reviewer").value(false));
    }

    @Test
    @WithMockUser(username = "alice")
    void setReviewers_passesTheNames_andRejectsABodyWithoutThem() throws Exception {
        when(reviewService.setReviewers(eq(5L), eq(List.of("bob", "carol")), eq("alice"))).thenReturn(review());

        mockMvc.perform(put("/api/measures/5/review/reviewers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernames\":[\"bob\",\"carol\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewers[0].username").value("bob"));

        mockMvc.perform(put("/api/measures/5/review/reviewers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "bob")
    void decision_andComments_carryTheCaller() throws Exception {
        when(reviewService.decide(5L, "approved", "ok", "bob")).thenReturn(review());
        when(reviewService.addComment(eq(5L), any(), eq("bob")))
                .thenReturn(MeasureReview.Comment.builder().id(77L).author("bob").body("Why 18?").section("populations").replies(List.of()).build());
        when(reviewService.setResolved(5L, 77L, true, "bob"))
                .thenReturn(MeasureReview.Comment.builder().id(77L).resolved(true).resolvedBy("bob").replies(List.of()).build());

        mockMvc.perform(post("/api/measures/5/review/decision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approved\",\"comment\":\"ok\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/measures/5/review/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Why 18?\",\"section\":\"populations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.author").value("bob"));
        mockMvc.perform(post("/api/measures/5/review/comments").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/measures/5/review/comments/77/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true));
        verify(reviewService).setResolved(5L, 77L, true, "bob");
    }
}
