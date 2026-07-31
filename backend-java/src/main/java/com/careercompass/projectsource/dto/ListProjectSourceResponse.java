package com.careercompass.projectsource.dto;

import java.time.Instant;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSourceType;

public record ListProjectSourceResponse(
        UUID projectSourceId,
        ProjectSourceType sourceType,
        String repositoryOwner,
        String repositoryName,
        String defaultBranch,
        Instant lastVerifiedAt
) {
}
