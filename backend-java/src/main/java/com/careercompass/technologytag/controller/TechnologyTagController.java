package com.careercompass.technologytag.controller;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.technologytag.dto.TechnologyTagSearchResponse;
import com.careercompass.technologytag.service.TechnologyTagQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/technology-tags")
public class TechnologyTagController {

    private final TechnologyTagQueryService queryService;
    private final ApiResponseFactory responseFactory;

    public TechnologyTagController(
            TechnologyTagQueryService queryService,
            ApiResponseFactory responseFactory
    ) {
        this.queryService = queryService;
        this.responseFactory = responseFactory;
    }

    @GetMapping
    public ApiResponse<TechnologyTagSearchResponse> searchTechnologyTags(
            @RequestParam(required = false) String query
    ) {
        return responseFactory.success(queryService.searchTechnologyTags(query));
    }
}
