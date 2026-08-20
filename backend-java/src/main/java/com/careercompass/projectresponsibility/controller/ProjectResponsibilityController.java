package com.careercompass.projectresponsibility.controller;

import java.util.UUID;
import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;

import com.careercompass.projectresponsibility.dto.ProjectResponsibilityDecisionRequest;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityDecisionResponse;
import com.careercompass.projectresponsibility.dto.ProjectResponsibilityReviewResponse;
import com.careercompass.projectresponsibility.dto.ProjectTechnologySuggestionDecisionRequest;
import com.careercompass.projectresponsibility.dto.ProjectTechnologySuggestionDecisionResponse;

import com.careercompass.projectresponsibility.service.ProjectResponsibilityReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PutMapping("/project-technology-suggestions/{suggestionId}/decision")
    public ApiResponse<ProjectTechnologySuggestionDecisionResponse> decideSuggestion(
            @PathVariable UUID suggestionId,
            @RequestBody ProjectTechnologySuggestionDecisionRequest request) {
        return responseFactory.success(
                service.decideSuggestion(suggestionId, request));
    }
}
