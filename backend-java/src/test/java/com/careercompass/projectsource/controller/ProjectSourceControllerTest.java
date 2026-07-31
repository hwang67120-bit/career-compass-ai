package com.careercompass.projectsource.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.projectsource.domain.ProjectSourceType;
import com.careercompass.projectsource.dto.ListProjectSourceResponse;
import com.careercompass.projectsource.service.ProjectSourceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectSourceControllerTest {

    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-31T03:00:00Z");

    private MockMvc mockMvc;
    private ProjectSourceQueryService service;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ProjectSourceQueryService.class);
        ApiResponseFactory responseFactory =
                new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC));
        ProjectSourceController controller =
                new ProjectSourceController(service, responseFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listProjectSources_withRegisteredSources_returnsCurrentUsersSources()
            throws Exception {
        when(service.listCurrentUserProjectSources()).thenReturn(List.of(
                new ListProjectSourceResponse(
                        PROJECT_SOURCE_ID,
                        ProjectSourceType.GITHUB_PUBLIC_REPOSITORY,
                        "octocat",
                        "Hello-World",
                        "master",
                        NOW
                )
        ));

        mockMvc.perform(get("/api/v1/project-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectSourceId")
                        .value(PROJECT_SOURCE_ID.toString()))
                .andExpect(jsonPath("$.data[0].sourceType")
                        .value("GITHUB_PUBLIC_REPOSITORY"))
                .andExpect(jsonPath("$.data[0].repositoryOwner")
                        .value("octocat"))
                .andExpect(jsonPath("$.data[0].repositoryName")
                        .value("Hello-World"))
                .andExpect(jsonPath("$.data[0].defaultBranch")
                        .value("master"))
                .andExpect(jsonPath("$.data[0].lastVerifiedAt")
                        .value("2026-07-31T03:00:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void listProjectSources_withoutRegisteredSources_returnsEmptyList()
            throws Exception {
        when(service.listCurrentUserProjectSources()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/project-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
