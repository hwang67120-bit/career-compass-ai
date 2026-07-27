package com.careercompass.projectsource.service;

import java.net.URI;

public record VerifiedGitHubRepository(
        String owner,
        String repository,
        URI canonicalUrl,
        String defaultBranch,
        String commitSha
) {
}
