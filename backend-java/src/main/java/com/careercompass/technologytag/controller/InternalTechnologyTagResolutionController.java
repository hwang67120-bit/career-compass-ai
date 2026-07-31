package com.careercompass.technologytag.controller;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.technologytag.dto.TechnologyTagResolutionRequest;
import com.careercompass.technologytag.dto.TechnologyTagResolutionResponse;
import com.careercompass.technologytag.service.TechnologyTagResolutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/technology-tags")
public class InternalTechnologyTagResolutionController {

    private final TechnologyTagResolutionService resolutionService;
    private final ApiResponseFactory responseFactory;

    public InternalTechnologyTagResolutionController(
            TechnologyTagResolutionService resolutionService,
            ApiResponseFactory responseFactory
    ) {
        this.resolutionService = resolutionService;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/resolve")
    public ApiResponse<TechnologyTagResolutionResponse> resolveTechnologyNames(
            @RequestBody TechnologyTagResolutionRequest request
    ) {
        return responseFactory.success(
                resolutionService.resolveTechnologyNames(
                        request.technologyNames()
                )
        );
    }
}
