package com.careercompass.projectresponsibility.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityCandidateResponse;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityDecisionResponse;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityReviewResponse;
import com.careercompass.projectresponsibility.exception.ProjectResponsibilityConflictException;
import com.careercompass.projectresponsibility.exception.ProjectResponsibilityExpiredException;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectResponsibilityControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");
    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private MockMvc mockMvc;
    private ProjectResponsibilityReviewService service;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ProjectResponsibilityReviewService.class);
        ApiResponseFactory responseFactory =
                new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC));
        ProjectResponsibilityController controller =
                new ProjectResponsibilityController(service, responseFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProjectResponsibilityExceptionHandler(responseFactory))
                .build();
    }

    @Test
    void retrieve_ownedProject_returnsUnconfirmedCandidates() throws Exception {
        when(service.retrieve(PROJECT_SOURCE_ID)).thenReturn(
                new ProjectResponsibilityReviewResponse(
                        PROJECT_SOURCE_ID, "a".repeat(40),
                        "AWAITING_USER_CONFIRMATION", null,
                        List.of(candidate("UNCONFIRMED", 0))));

        mockMvc.perform(get("/api/v1/project-sources/{projectSourceId}/responsibility-candidates",
                        PROJECT_SOURCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectSourceId")
                        .value(PROJECT_SOURCE_ID.toString()))
                .andExpect(jsonPath("$.data.candidates[0].category")
                        .value("PROJECT_RESPONSIBILITY"))
                .andExpect(jsonPath("$.data.candidates[0].status")
                        .value("UNCONFIRMED"));
    }

    @Test
    void decide_validConfirmation_returnsReviewResult() throws Exception {
        when(service.decide(any(), any())).thenReturn(
                new ProjectResponsibilityDecisionResponse(
                        candidate("CONFIRMED", 1), true, null));

        mockMvc.perform(put("/api/v1/project-responsibility-candidates/{candidateId}/decision",
                        CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":0,
                                  "decision":"CONFIRM",
                                  "confirmedText":"Spring Boot 주문 API 구현"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCompleted").value(true))
                .andExpect(jsonPath("$.data.candidate.status").value("CONFIRMED"));
    }

    @Test
    void decide_staleVersion_returnsVersionConflict() throws Exception {
        when(service.decide(any(), any()))
                .thenThrow(new ProjectResponsibilityConflictException());

        mockMvc.perform(put("/api/v1/project-responsibility-candidates/{candidateId}/decision",
                        CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"decision":"REJECT","confirmedText":null}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorType")
                        .value("PROJECT_RESPONSIBILITY_CANDIDATE_VERSION_CONFLICT"));
    }

    @Test
    void decide_expiredCandidate_returnsGone() throws Exception {
        when(service.decide(any(), any()))
                .thenThrow(new ProjectResponsibilityExpiredException());

        mockMvc.perform(put("/api/v1/project-responsibility-candidates/{candidateId}/decision",
                        CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"decision":"REJECT","confirmedText":null}
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.errorType")
                        .value("PROJECT_RESPONSIBILITY_CANDIDATE_EXPIRED"));
    }

    private ProjectResponsibilityCandidateResponse candidate(String status, long version) {
        return new ProjectResponsibilityCandidateResponse(
                CANDIDATE_ID, "PROJECT_RESPONSIBILITY",
                "Spring Boot 주문 API 구현",
                "CONFIRMED".equals(status) ? "Spring Boot 주문 API 구현" : null,
                status, version, List.of(), List.of(),
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                "UNCONFIRMED".equals(status) ? null : NOW);
    }
}
