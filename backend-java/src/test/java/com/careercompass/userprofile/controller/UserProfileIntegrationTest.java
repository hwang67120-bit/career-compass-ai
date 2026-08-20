package com.careercompass.userprofile.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
        "test.user-id=30000000-0000-0000-0000-000000000001"
})
class UserProfileIntegrationTest {

    private static final String PATH = "/api/v1/user-profile";
    private static final UUID TEST_USER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
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
    void resetUserProfile() {
        jdbcTemplate.update(
                "DELETE FROM user_account WHERE id = ?",
                TEST_USER_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (id, user_status, created_at)
                VALUES (?, 'ACTIVE', ?)
                """,
                TEST_USER_ID,
                Timestamp.from(Instant.parse("2026-08-03T00:00:00Z"))
        );
    }

    @Test
    void saveUserProfile_withSelectedAliasAndCustomTags_persistsVersionSnapshot()
            throws Exception {
        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "백엔드 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    },
                                    {
                                      "technologyTagId": null,
                                      "customName": "Spring"
                                    },
                                    {
                                      "technologyTagId": null,
                                      "customName": "LangChain"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.targetJobTitle")
                        .value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.technologyTags.length()")
                        .value(3))
                .andExpect(jsonPath("$.data.technologyTags[0].rawName")
                        .value("LangChain"))
                .andExpect(jsonPath("$.data.technologyTags[0].technologyTagId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.technologyTags[0].sourceType")
                        .value("USER_CUSTOM"))
                .andExpect(jsonPath("$.data.technologyTags[1].displayName")
                        .value("Java"))
                .andExpect(jsonPath("$.data.technologyTags[1].sourceType")
                        .value("USER_SELECTED"))
                .andExpect(jsonPath("$.data.technologyTags[2].rawName")
                        .value("Spring"))
                .andExpect(jsonPath("$.data.technologyTags[2].displayName")
                        .value("Spring Framework"))
                .andExpect(jsonPath("$.data.technologyTags[2].technologyTagId")
                        .value("70000000-0000-0000-0000-000000000012"))
                .andExpect(jsonPath("$.data.technologyTags[2].sourceType")
                        .value("USER_CUSTOM"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.technologyTags[2].rawName")
                        .value("Spring"));
    }

    @Test
    void saveUserProfile_withSameNormalizedContent_reusesCurrentVersion()
            throws Exception {
        saveInitialProfile();

        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "  백엔드   개발자  ",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": null,
                                      "customName": "LangChain"
                                    },
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_profile_version",
                Integer.class
        );
        org.assertj.core.api.Assertions.assertThat(versionCount).isEqualTo(1);
    }

    @Test
    void saveUserProfile_withChangedContentAndCurrentVersion_createsNextVersion()
            throws Exception {
        saveInitialProfile();

        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 1,
                                  "targetJobTitle": "플랫폼 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.targetJobTitle")
                        .value("플랫폼 개발자"));

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_profile_version",
                Integer.class
        );
        String firstVersionTitle = jdbcTemplate.queryForObject(
                """
                SELECT target_job_title
                FROM user_profile_version
                WHERE profile_version = 1
                """,
                String.class
        );
        org.assertj.core.api.Assertions.assertThat(versionCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(firstVersionTitle)
                .isEqualTo("백엔드 개발자");
    }

    @Test
    void saveUserProfile_withChangedContentAndStaleVersion_returnsConflict()
            throws Exception {
        saveInitialProfile();

        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion": 7,
                                  "targetJobTitle": "플랫폼 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.errorType")
                        .value("USER_PROFILE_VERSION_CONFLICT"));
    }

    @Test
    void saveUserProfile_withBothTagFields_returnsBadRequest()
            throws Exception {
        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "백엔드 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": "Java"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_USER_PROFILE"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("technologyTags[0]"));
    }

    @Test
    void retrieveUserProfile_withoutSavedProfile_returnsNotFound()
            throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.errorType")
                        .value("USER_PROFILE_NOT_FOUND"));
    }

    private void saveInitialProfile() throws Exception {
        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetJobTitle": "백엔드 개발자",
                                  "technologyTags": [
                                    {
                                      "technologyTagId": "70000000-0000-0000-0000-000000000001",
                                      "customName": null
                                    },
                                    {
                                      "technologyTagId": null,
                                      "customName": "LangChain"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }
}
