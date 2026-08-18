package com.careercompass.projectresponsibility.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProjectResponsibilityCandidateDraft(
        String text,
        List<String> sourceEvidenceIds,
        Set<UUID> technologyTagIds
) {
}
