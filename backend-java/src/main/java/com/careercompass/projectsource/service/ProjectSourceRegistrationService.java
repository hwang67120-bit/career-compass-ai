package com.careercompass.projectsource.service;

import java.time.Instant;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSourceRegistrationService {

    private final ProjectSourceRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public ProjectSourceRegistrationService(
            ProjectSourceRepository repository,
            CurrentUserProvider currentUserProvider
    ) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 기능: 검증된 GitHub 저장소의 현재 버전을 현재 사용자 자료로 저장한다.
     * 반환 값: 등록 식별자, 정규 저장소 주소, 기본 브랜치, 커밋 식별자와 상태를 반환한다.
     */
    @Transactional
    public CreateGitHubProjectSourceResponse createGitHubProjectSource(
            UUID projectSourceId,
            VerifiedGitHubRepository verifiedRepository,
            Instant createdAt
    ) {
        ProjectSource projectSource = ProjectSource.create(
                projectSourceId,
                currentUserProvider.getCurrentUserId(),
                verifiedRepository.canonicalUrl().toString(),
                verifiedRepository.owner() + "/" + verifiedRepository.repository(),
                verifiedRepository.defaultBranch(),
                verifiedRepository.commitSha(),
                createdAt
        );
        ProjectSource savedProjectSource = repository.save(projectSource);
        return new CreateGitHubProjectSourceResponse(
                savedProjectSource.getId(),
                savedProjectSource.getRepositoryUrl(),
                savedProjectSource.getRepositoryFullName(),
                savedProjectSource.getDefaultBranch(),
                savedProjectSource.getCommitSha(),
                savedProjectSource.getProjectSourceStatus()
        );
    }
}
