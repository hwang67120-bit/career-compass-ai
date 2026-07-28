package com.careercompass.projectsource.dto;

import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSourceStatus;

public record CreateGitHubProjectSourceResponse(
        UUID projectSourceId,
        String repositoryUrl,
        String repositoryFullName,
        String defaultBranch,
        String commitSha,
        ProjectSourceStatus status
) {
}
