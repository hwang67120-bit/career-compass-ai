package com.careercompass.projectresponsibility.controller;

import java.util.UUID;
import com.careercompass.common.web.*;
import com.careercompass.projectresponsibility.dto.*;
import com.careercompass.projectresponsibility.service.ProjectResponsibilityReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProjectResponsibilityController {
    private final ProjectResponsibilityReviewService service;
    private final ApiResponseFactory responseFactory;

    public ProjectResponsibilityController(
            ProjectResponsibilityReviewService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/project-sources/{projectSourceId}/responsibility-candidates")
    public ApiResponse<ProjectResponsibilityReviewResponse> retrieve(
            @PathVariable UUID projectSourceId) {
        return responseFactory.success(service.retrieve(projectSourceId));
    }

    @PutMapping("/project-responsibility-candidates/{candidateId}/decision")
    public ApiResponse<ProjectResponsibilityDecisionResponse> decide(
            @PathVariable UUID candidateId,
            @RequestBody ProjectResponsibilityDecisionRequest request) {
        return responseFactory.success(service.decide(candidateId, request));
    }
}
