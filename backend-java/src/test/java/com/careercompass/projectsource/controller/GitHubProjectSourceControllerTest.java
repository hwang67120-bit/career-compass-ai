package com.careercompass.projectsource.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.common.web.GlobalExceptionHandler;
import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
import com.careercompass.projectsource.service.ProjectSourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class GitHubProjectSourceControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");
    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final String COMMIT_SHA =
            "0123456789abcdef0123456789abcdef01234567";

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private ProjectSourceService projectSourceService;

    @BeforeEach
    void setUp() {
        projectSourceService = Mockito.mock(ProjectSourceService.class);
        ApiResponseFactory responseFactory =
                new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC));
        GitHubProjectSourceController controller =
                new GitHubProjectSourceController(projectSourceService, responseFactory);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(responseFactory))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @Test
    void createGitHubProjectSource_withPublicRepository_returnsCreatedSource()
            throws Exception {
        when(projectSourceService.createGitHubProjectSource(any()))
                .thenReturn(new CreateGitHubProjectSourceResponse(
                        PROJECT_SOURCE_ID,
                        "https://github.com/octocat/Hello-World",
                        "octocat/Hello-World",
                        "master",
                        COMMIT_SHA,
                        ProjectSourceStatus.REGISTERED
                ));

        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://github.com/octocat/Hello-World"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/project-sources/" + PROJECT_SOURCE_ID
                ))
                .andExpect(jsonPath("$.data.projectSourceId")
                        .value(PROJECT_SOURCE_ID.toString()))
                .andExpect(jsonPath("$.data.repositoryFullName")
                        .value("octocat/Hello-World"))
                .andExpect(jsonPath("$.data.defaultBranch").value("master"))
                .andExpect(jsonPath("$.data.commitSha").value(COMMIT_SHA))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void createGitHubProjectSource_withBlankUrl_returnsBadRequest()
            throws Exception {
        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("repositoryUrl"));
    }

    @Test
    void createGitHubProjectSource_withInvalidHost_returnsBadRequest()
            throws Exception {
        when(projectSourceService.createGitHubProjectSource(any()))
                .thenThrow(new InvalidGitHubRepositoryUrlException());

        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://example.com/repository"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_GITHUB_REPOSITORY_URL"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("repositoryUrl"));
    }

    @Test
    void createGitHubProjectSource_withUnavailableRepository_returnsNotFound()
            throws Exception {
        when(projectSourceService.createGitHubProjectSource(any()))
                .thenThrow(new GitHubAccessException(
                        GitHubAccessFailure.REPOSITORY_UNAVAILABLE
                ));

        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://github.com/octocat/missing"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.errorType")
                        .value("GITHUB_REPOSITORY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void createGitHubProjectSource_whenGitHubRateLimited_returnsTooManyRequests()
            throws Exception {
        when(projectSourceService.createGitHubProjectSource(any()))
                .thenThrow(new GitHubAccessException(
                        GitHubAccessFailure.RATE_LIMITED
                ));

        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://github.com/octocat/Hello-World"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.errorType")
                        .value("GITHUB_RATE_LIMITED"))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }

    @Test
    void createGitHubProjectSource_whenGitHubUnavailable_returnsServiceUnavailable()
            throws Exception {
        when(projectSourceService.createGitHubProjectSource(any()))
                .thenThrow(new GitHubAccessException(
                        GitHubAccessFailure.SERVICE_UNAVAILABLE
                ));

        mockMvc.perform(post("/api/v1/project-sources/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repositoryUrl":"https://github.com/octocat/Hello-World"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.errorType")
                        .value("GITHUB_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }
}
