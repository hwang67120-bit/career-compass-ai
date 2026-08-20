package com.careercompass.projectresponsibility.dto;

import java.util.List;
import java.util.UUID;

public record ProjectResponsibilityReviewResponse(
        UUID projectSourceId, String repositoryVersion, String reviewStatus,
        String extractionStatus, String failureCode, List<UUID> failedTechnologyTagIds,
        UUID linkedJobAnalysisId,
        List<ProjectTechnologyFindingResponse> selectedTechnologyFindings,
        List<ProjectTechnologySuggestionResponse> technologySuggestions,
        List<ProjectResponsibilityCandidateResponse> candidates
) {}
