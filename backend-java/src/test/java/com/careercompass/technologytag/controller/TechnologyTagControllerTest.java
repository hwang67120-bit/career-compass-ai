package com.careercompass.technologytag.controller;

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
import com.careercompass.technologytag.domain.TechnologyTagCategory;
import com.careercompass.technologytag.dto.TechnologyTagResponse;
import com.careercompass.technologytag.dto.TechnologyTagSearchResponse;
import com.careercompass.technologytag.exception.InvalidTechnologyTagQueryException;
import com.careercompass.technologytag.service.TechnologyTagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TechnologyTagControllerTest {

    private static final UUID TECHNOLOGY_TAG_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000026");
    private static final Instant NOW = Instant.parse("2026-07-31T07:00:00Z");

    private MockMvc mockMvc;
    private TechnologyTagQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = Mockito.mock(TechnologyTagQueryService.class);
        ApiResponseFactory responseFactory =
                new ApiResponseFactory(Clock.fixed(NOW, ZoneOffset.UTC));
        TechnologyTagController controller =
                new TechnologyTagController(queryService, responseFactory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TechnologyTagExceptionHandler(responseFactory))
                .build();
    }

    @Test
    void searchTechnologyTags_withAliasQuery_returnsStandardTag()
            throws Exception {
        when(queryService.searchTechnologyTags("k8s"))
                .thenReturn(new TechnologyTagSearchResponse(List.of(
                        new TechnologyTagResponse(
                                TECHNOLOGY_TAG_ID,
                                "kubernetes",
                                "Kubernetes",
                                TechnologyTagCategory.INFRASTRUCTURE_CLOUD,
                                "K8s"
                        )
                )));

        mockMvc.perform(get("/api/v1/technology-tags").queryParam("query", "k8s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.technologyTags[0].technologyTagId")
                        .value(TECHNOLOGY_TAG_ID.toString()))
                .andExpect(jsonPath("$.data.technologyTags[0].key")
                        .value("kubernetes"))
                .andExpect(jsonPath("$.data.technologyTags[0].matchedAlias")
                        .value("K8s"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void searchTechnologyTags_withTooLongQuery_returnsBadRequest()
            throws Exception {
        when(queryService.searchTechnologyTags("query"))
                .thenThrow(new InvalidTechnologyTagQueryException());

        mockMvc.perform(get("/api/v1/technology-tags").queryParam("query", "query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType")
                        .value("INVALID_TECHNOLOGY_TAG_QUERY"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName")
                        .value("query"));
    }
}
