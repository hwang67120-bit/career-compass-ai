package com.careercompass.projectsource.controller;

import java.net.URI;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceRequest;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.service.ProjectSourceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/project-sources/github")
public class GitHubProjectSourceController {

    private final ProjectSourceService projectSourceService;
    private final ApiResponseFactory responseFactory;

    public GitHubProjectSourceController(
            ProjectSourceService projectSourceService,
            ApiResponseFactory responseFactory
    ) {
        this.projectSourceService = projectSourceService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGitHubProjectSourceResponse>>
    createGitHubProjectSource(
            @Valid @RequestBody CreateGitHubProjectSourceRequest request
    ) {
        CreateGitHubProjectSourceResponse response =
                projectSourceService.createGitHubProjectSource(request);
        return ResponseEntity
                .created(URI.create("/api/v1/project-sources/" + response.projectSourceId()))
                .body(responseFactory.success(response));
    }
}
