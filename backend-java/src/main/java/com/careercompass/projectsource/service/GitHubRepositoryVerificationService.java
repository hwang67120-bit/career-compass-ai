package com.careercompass.projectsource.service;

import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRepositoryMetadata;
import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import org.springframework.stereotype.Service;

@Service
public class GitHubRepositoryVerificationService {

    private final GitHubRepositoryGateway repositoryGateway;

    public GitHubRepositoryVerificationService(GitHubRepositoryGateway repositoryGateway) {
        this.repositoryGateway = repositoryGateway;
    }

    /**
     * 기능: 사용자 GitHub URL의 경계를 검증하고 실제 공개 저장소와 현재 버전을 확인한다.
     * 반환 값: 정규화된 저장소 주소, 기본 브랜치와 현재 커밋 식별자를 반환한다.
     */
    public VerifiedGitHubRepository verifyRepository(String repositoryUrl) {
        GitHubRepositoryCoordinates coordinates =
                GitHubRepositoryCoordinates.createFromUrl(repositoryUrl);
        GitHubRepositoryMetadata metadata = repositoryGateway.fetchRepository(coordinates);
        validateMetadata(coordinates, metadata);

        String commitSha = repositoryGateway.fetchLatestCommitSha(
                coordinates, metadata.defaultBranch());
        if (commitSha == null || commitSha.isBlank()) {
            throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE);
        }

        return new VerifiedGitHubRepository(
                coordinates.owner(),
                coordinates.repository(),
                coordinates.canonicalUrl(),
                metadata.defaultBranch(),
                commitSha
        );
    }

    private void validateMetadata(
            GitHubRepositoryCoordinates coordinates,
            GitHubRepositoryMetadata metadata
    ) {
        if (metadata == null
                || metadata.privateRepository()
                || metadata.disabled()
                || metadata.fullName() == null
                || !coordinates.fullName().equalsIgnoreCase(metadata.fullName())
                || metadata.defaultBranch() == null
                || metadata.defaultBranch().isBlank()) {
            throw new GitHubAccessException(GitHubAccessFailure.REPOSITORY_UNAVAILABLE);
        }
    }
}
