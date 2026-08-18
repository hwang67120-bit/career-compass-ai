package com.careercompass.projectresponsibility.dto;

import java.util.List;
import java.util.UUID;

public record ProjectTechnologyFindingResponse(
        UUID technologyTagId,
        String canonicalName,
        String findingStatus,
        List<ProjectResponsibilityEvidenceResponse> sourceEvidence
) {}
