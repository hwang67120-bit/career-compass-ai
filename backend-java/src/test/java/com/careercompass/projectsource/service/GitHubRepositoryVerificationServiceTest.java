package com.careercompass.projectsource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;

import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRepositoryMetadata;
import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class GitHubRepositoryVerificationServiceTest {

    private static final String REPOSITORY_URL =
            "https://github.com/octocat/Hello-World";
    private static final String COMMIT_SHA =
            "7fd1a60b01f91b314f59955a4e4d92aa1d1f36f3";

    private GitHubRepositoryGateway repositoryGateway;
    private GitHubRepositoryVerificationService verificationService;

    @BeforeEach
    void setUp() {
        repositoryGateway = mock(GitHubRepositoryGateway.class);
        verificationService = new GitHubRepositoryVerificationService(repositoryGateway);
    }

    @Test
    void verifyRepository_withPublicRepository_returnsVerifiedCurrentVersion() {
        when(repositoryGateway.fetchRepository(ArgumentMatchers.any()))
                .thenReturn(new GitHubRepositoryMetadata(
                        "octocat/Hello-World", false, "main", false));
        when(repositoryGateway.fetchLatestCommitSha(
                ArgumentMatchers.any(), ArgumentMatchers.eq("main")))
                .thenReturn(COMMIT_SHA);

        VerifiedGitHubRepository repository =
                verificationService.verifyRepository(REPOSITORY_URL);

        assertThat(repository.owner()).isEqualTo("octocat");
        assertThat(repository.repository()).isEqualTo("Hello-World");
        assertThat(repository.canonicalUrl())
                .isEqualTo(URI.create(REPOSITORY_URL));
        assertThat(repository.defaultBranch()).isEqualTo("main");
        assertThat(repository.commitSha()).isEqualTo(COMMIT_SHA);
    }

    @Test
    void verifyRepository_withInvalidUrl_rejectsBeforeExternalRequest() {
        assertThatThrownBy(() -> verificationService.verifyRepository(
                "https://evil.example/octocat/Hello-World"))
                .isInstanceOf(InvalidGitHubRepositoryUrlException.class);
        verifyNoInteractions(repositoryGateway);
    }

    @Test
    void verifyRepository_withPrivateRepositoryResponse_rejectsRepository() {
        when(repositoryGateway.fetchRepository(ArgumentMatchers.any()))
                .thenReturn(new GitHubRepositoryMetadata(
                        "octocat/Hello-World", true, "main", false));

        assertUnavailable();
    }

    @Test
    void verifyRepository_withDisabledRepositoryResponse_rejectsRepository() {
        when(repositoryGateway.fetchRepository(ArgumentMatchers.any()))
                .thenReturn(new GitHubRepositoryMetadata(
                        "octocat/Hello-World", false, "main", true));

        assertUnavailable();
    }

    @Test
    void verifyRepository_withMismatchedRepositoryResponse_rejectsRepository() {
        when(repositoryGateway.fetchRepository(ArgumentMatchers.any()))
                .thenReturn(new GitHubRepositoryMetadata(
                        "other/Repository", false, "main", false));

        assertUnavailable();
    }

    @Test
    void verifyRepository_withMissingCommitSha_rejectsResponse() {
        when(repositoryGateway.fetchRepository(ArgumentMatchers.any()))
                .thenReturn(new GitHubRepositoryMetadata(
                        "octocat/Hello-World", false, "main", false));
        when(repositoryGateway.fetchLatestCommitSha(
                ArgumentMatchers.any(), ArgumentMatchers.eq("main")))
                .thenReturn(" ");

        assertThatThrownBy(() -> verificationService.verifyRepository(REPOSITORY_URL))
                .isInstanceOfSatisfying(GitHubAccessException.class,
                        exception -> assertThat(exception.getFailure())
                                .isEqualTo(GitHubAccessFailure.INVALID_RESPONSE));
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> verificationService.verifyRepository(REPOSITORY_URL))
                .isInstanceOfSatisfying(GitHubAccessException.class,
                        exception -> assertThat(exception.getFailure())
                                .isEqualTo(GitHubAccessFailure.REPOSITORY_UNAVAILABLE));
    }
}
