package com.careercompass.projectsource.client;

public record GitHubRepositoryBlob(
        String content,
        String encoding,
        long size
) {
}
