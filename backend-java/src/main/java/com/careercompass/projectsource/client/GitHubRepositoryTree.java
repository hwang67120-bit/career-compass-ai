package com.careercompass.projectsource.client;

import java.util.List;

public record GitHubRepositoryTree(
        List<Entry> entries,
        boolean truncated
) {
    public record Entry(String path, String type, String sha, long size) {
    }
}
