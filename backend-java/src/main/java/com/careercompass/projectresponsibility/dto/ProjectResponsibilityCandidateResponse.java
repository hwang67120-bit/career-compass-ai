package com.careercompass.projectresponsibility.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponsibilityCandidateResponse(
        UUID candidateId,
        String category,
        String extractedText,
        String confirmedText,
        String status,
        long version,
        List<ProjectResponsibilityTechnologyTagResponse> relatedTechnologyTags,
        List<ProjectResponsibilityEvidenceResponse> sourceEvidence,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt
) {
}
