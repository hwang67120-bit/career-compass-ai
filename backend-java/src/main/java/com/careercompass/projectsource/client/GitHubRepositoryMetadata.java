package com.careercompass.projectsource.client;

public record GitHubRepositoryMetadata(
        String fullName,
        boolean privateRepository,
        String defaultBranch,
        boolean disabled
) {
}
