package com.careercompass.projectresponsibility.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectTechnologySuggestionResponse(
        UUID suggestionId,
        UUID technologyTagId,
        String canonicalName,
        String decisionStatus,
        long version,
        List<ProjectResponsibilityEvidenceResponse> sourceEvidence,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt
) {}
