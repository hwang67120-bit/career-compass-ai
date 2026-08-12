package com.careercompass.projectresponsibility.dto;

import java.util.List;
import java.util.UUID;

public record ProjectResponsibilityReviewResponse(
        UUID projectSourceId, String repositoryVersion, String reviewStatus,
        UUID linkedJobAnalysisId, List<ProjectResponsibilityCandidateResponse> candidates) {}
