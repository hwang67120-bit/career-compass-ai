package com.careercompass.technologytag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
        "technology-tag.resolution.max-names=30",
        "technology-tag.resolution.max-name-length=100"
})
class TechnologyTagResolutionIntegrationTest {

    private static final String PATH =
            "/internal/v1/technology-tags/resolve";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resolveTechnologyNames_withValidToken_returnsCanonicalAliasAndUnresolvedResults()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Internal-Token", "integration-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "technologyNames": [
                                    "Kubernetes",
                                    "k8s",
                                    "C",
                                    "C++",
                                    "C#",
                                    "unknown-tool"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(6))
                .andExpect(jsonPath("$.data.results[0].canonicalName")
                        .value("Kubernetes"))
                .andExpect(jsonPath("$.data.results[0].matchMethod")
                        .value("CANONICAL"))
                .andExpect(jsonPath("$.data.results[1].technologyTagId")
                        .value("70000000-0000-0000-0000-000000000026"))
                .andExpect(jsonPath("$.data.results[1].matchMethod")
                        .value("ALIAS"))
                .andExpect(jsonPath("$.data.results[2].canonicalName")
                        .value("C"))
                .andExpect(jsonPath("$.data.results[3].canonicalName")
                        .value("C++"))
                .andExpect(jsonPath("$.data.results[4].canonicalName")
                        .value("C#"))
                .andExpect(jsonPath("$.data.results[5].rawName")
                        .value("unknown-tool"))
                .andExpect(jsonPath("$.data.results[5].technologyTagId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.results[5].matchStatus")
                        .value("UNRESOLVED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void resolveTechnologyNames_withoutInternalToken_returnsUnauthorizedEnvelope()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technologyNames":["Java"]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INTERNAL_UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    void resolveTechnologyNames_withInvalidInternalToken_returnsUnauthorizedEnvelope()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Internal-Token", "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technologyNames":["Java"]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INTERNAL_UNAUTHORIZED"));
    }

    @Test
    void resolveTechnologyNames_withBlankName_returnsBadRequestEnvelope()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Internal-Token", "integration-test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technologyNames":["Java"," "]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_TECHNOLOGY_TAG_RESOLUTION_REQUEST"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("technologyNames[1]"));
    }
}
