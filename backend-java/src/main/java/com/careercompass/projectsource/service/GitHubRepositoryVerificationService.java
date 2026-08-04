package com.careercompass.projectsource.service;

import java.util.concurrent.TimeUnit;

import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRepositoryMetadata;
import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubRepositoryVerificationService {

    private static final Logger log =
            LoggerFactory.getLogger(GitHubRepositoryVerificationService.class);

    private final GitHubRepositoryGateway repositoryGateway;

    public GitHubRepositoryVerificationService(GitHubRepositoryGateway repositoryGateway) {
        this.repositoryGateway = repositoryGateway;
    }

    /**
     * 기능: 사용자 GitHub URL의 경계를 검증하고 실제 공개 저장소와 현재 버전을 확인한다.
     * 반환 값: 정규화된 저장소 주소, 기본 브랜치와 현재 커밋 식별자를 반환한다.
     */
    public VerifiedGitHubRepository verifyRepository(String repositoryUrl) {
        long startedAt = System.nanoTime();
        GitHubRepositoryCoordinates coordinates =
                GitHubRepositoryCoordinates.createFromUrl(repositoryUrl);
        try {
            GitHubRepositoryMetadata metadata = repositoryGateway.fetchRepository(coordinates);
            validateMetadata(coordinates, metadata);

            String commitSha = repositoryGateway.fetchLatestCommitSha(
                    coordinates, metadata.defaultBranch());
            if (commitSha == null || commitSha.isBlank()) {
                throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE);
            }

            log.info("github_repository_verification_completed status=success durationMs={}",
                    elapsedMillis(startedAt));
            return new VerifiedGitHubRepository(
                    coordinates.owner(),
                    coordinates.repository(),
                    coordinates.canonicalUrl(),
                    metadata.defaultBranch(),
                    commitSha
            );
        } catch (GitHubAccessException exception) {
            log.warn("github_repository_verification_completed status=failed failure={} durationMs={}",
                    exception.getFailure(), elapsedMillis(startedAt));
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
