package com.careercompass.projectresponsibility.dto;

import java.util.UUID;

public record ProjectTechnologySuggestionDecisionResponse(
        ProjectTechnologySuggestionResponse suggestion,
        boolean reviewCompleted,
        UUID resumedJobAnalysisId
) {}
