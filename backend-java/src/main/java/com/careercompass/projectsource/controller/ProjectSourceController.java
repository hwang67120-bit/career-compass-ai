package com.careercompass.projectsource.controller;

import java.util.List;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.projectsource.dto.ListProjectSourceResponse;
import com.careercompass.projectsource.service.ProjectSourceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/project-sources")
public class ProjectSourceController {

    private final ProjectSourceQueryService projectSourceQueryService;
    private final ApiResponseFactory responseFactory;

    public ProjectSourceController(
            ProjectSourceQueryService projectSourceQueryService,
            ApiResponseFactory responseFactory
    ) {
        this.projectSourceQueryService = projectSourceQueryService;
        this.responseFactory = responseFactory;
    }

    @GetMapping
    public ApiResponse<List<ListProjectSourceResponse>> listProjectSources() {
        return responseFactory.success(
                projectSourceQueryService.listCurrentUserProjectSources()
        );
    }
}
