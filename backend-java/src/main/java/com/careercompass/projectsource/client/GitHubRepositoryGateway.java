package com.careercompass.projectsource.client;

import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;

public interface GitHubRepositoryGateway {

    GitHubRepositoryMetadata fetchRepository(GitHubRepositoryCoordinates coordinates);

    String fetchLatestCommitSha(GitHubRepositoryCoordinates coordinates, String defaultBranch);
}
