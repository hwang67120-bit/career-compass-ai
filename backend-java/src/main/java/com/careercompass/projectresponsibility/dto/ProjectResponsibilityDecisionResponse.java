package com.careercompass.projectresponsibility.dto;

import java.util.UUID;

public record ProjectResponsibilityDecisionResponse(
        ProjectResponsibilityCandidateResponse candidate,
        boolean reviewCompleted,
        UUID resumedJobAnalysisId) {}
