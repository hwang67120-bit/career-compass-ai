package com.careercompass.jobanalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "github.api.connect-timeout=3s",
        "github.api.read-timeout=8s",
        "python.worker.internal-token=integration-test-token",
        "work24.api.auth-key=integration-test-key",
        "test.user-id=30000000-0000-0000-0000-000000000001"
})
class JobAnalysisIntegrationTest {

    private static final String PATH = "/api/v1/job-analyses";
    private static final UUID TEST_USER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );
    private static final UUID OTHER_USER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000002"
    );
    private static final UUID PROJECT_SOURCE_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000001"
    );
    private static final UUID OTHER_PROJECT_SOURCE_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000002"
    );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAnalysisInputs() {
        jdbcTemplate.update(
                "DELETE FROM user_account WHERE id IN (?, ?)",
                TEST_USER_ID,
                OTHER_USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM project_source WHERE id IN (?, ?)",
                PROJECT_SOURCE_ID,
                OTHER_PROJECT_SOURCE_ID
        );
        insertUser(TEST_USER_ID);
        insertUser(OTHER_USER_ID);
        insertProjectSource(PROJECT_SOURCE_ID, TEST_USER_ID, "current/repo");
        insertProjectSource(
                OTHER_PROJECT_SOURCE_ID,
                OTHER_USER_ID,
                "other/repo"
        );
    }

    @Test
    void createJobAnalysis_withOwnedProfileVersionAndSource_savesQueuedJob()
            throws Exception {
        UUID userProfileId = saveProfile();

        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                userProfileId,
                                1,
                                sourceIdJson(PROJECT_SOURCE_ID)
                        )))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.startsWith(PATH + "/")
                ))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        UUID jobAnalysisId = UUID.fromString(
                location.substring((PATH + "/").length())
        );
        Map<String, Object> saved = jdbcTemplate.queryForMap(
                """
                SELECT user_id, user_profile_id, user_profile_version,
                       analysis_status, current_step, completed_units,
                       total_units
                FROM job_analysis
                WHERE id = ?
                """,
                jobAnalysisId
        );

        assertThat(saved.get("user_id")).isEqualTo(TEST_USER_ID);
        assertThat(saved.get("user_profile_id")).isEqualTo(userProfileId);
        assertThat(saved.get("user_profile_version")).isEqualTo(1);
        assertThat(saved.get("analysis_status")).isEqualTo("QUEUED");
        assertThat(saved.get("current_step"))
                .isEqualTo("VALIDATING_INPUTS");
        assertThat(saved.get("completed_units")).isEqualTo(0);
        assertThat(saved.get("total_units")).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM job_analysis_project_source
                WHERE job_analysis_id = ?
                  AND project_source_id = ?
                  AND selection_order = 0
                """,
                Integer.class,
                jobAnalysisId,
                PROJECT_SOURCE_ID
        )).isEqualTo(1);
    }

    @Test
    void createJobAnalysis_withMissingProfileVersion_returnsNotFound()
            throws Exception {
        UUID userProfileId = saveProfile();

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                userProfileId,
                                2,
                                sourceIdJson(PROJECT_SOURCE_ID)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.errorType")
                        .value("JOB_ANALYSIS_INPUT_NOT_FOUND"));

        assertThat(countJobAnalyses()).isZero();
    }

    @Test
    void createJobAnalysis_withAnotherUsersSource_returnsNotFound()
            throws Exception {
        UUID userProfileId = saveProfile();

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                userProfileId,
                                1,
                                sourceIdJson(OTHER_PROJECT_SOURCE_ID)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.errorType")
                        .value("JOB_ANALYSIS_INPUT_NOT_FOUND"));

        assertThat(countJobAnalyses()).isZero();
    }

    @Test
    void createJobAnalysis_withDuplicateSourceIds_returnsBadRequest()
            throws Exception {
        UUID userProfileId = saveProfile();
        String sourceIds = "\"%s\", \"%s\"".formatted(
                PROJECT_SOURCE_ID,
                PROJECT_SOURCE_ID
        );

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(userProfileId, 1, sourceIds)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_JOB_ANALYSIS_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("projectSourceIds"));

        assertThat(countJobAnalyses()).isZero();
    }

    @Test
    void createJobAnalysis_withoutSourceIds_returnsBadRequest()
            throws Exception {
        UUID userProfileId = saveProfile();

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(userProfileId, 1, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_JOB_ANALYSIS_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("projectSourceIds"));

        assertThat(countJobAnalyses()).isZero();
    }

    private UUID saveProfile() throws Exception {
        mockMvc.perform(put("/api/v1/user-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "백엔드 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_profile WHERE user_id = ?",
                UUID.class,
                TEST_USER_ID
        );
    }

    private String requestBody(
            UUID userProfileId,
            int userProfileVersion,
            String sourceIds
    ) {
        return """
                {
                  "userProfileId": "%s",
                  "userProfileVersion": %d,
                  "projectSourceIds": [%s]
                }
                """.formatted(
                userProfileId,
                userProfileVersion,
                sourceIds
        );
    }

    private String sourceIdJson(UUID projectSourceId) {
        return "\"%s\"".formatted(projectSourceId);
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (id, user_status, created_at)
                VALUES (?, 'ACTIVE', ?)
                """,
                userId,
                Timestamp.from(Instant.parse("2026-08-04T00:00:00Z"))
        );
    }

    private void insertProjectSource(
            UUID projectSourceId,
            UUID userId,
            String repositoryFullName
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO project_source (
                    id, user_id, repository_url, repository_full_name,
                    default_branch, commit_sha, project_source_status,
                    created_at
                )
                VALUES (?, ?, ?, ?, 'main', ?, 'REGISTERED', ?)
                """,
                projectSourceId,
                userId,
                "https://github.com/" + repositoryFullName,
                repositoryFullName,
                "0123456789012345678901234567890123456789",
                Timestamp.from(Instant.parse("2026-08-04T00:00:00Z"))
        );
    }

    private int countJobAnalyses() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_analysis",
                Integer.class
        );
    }
}
