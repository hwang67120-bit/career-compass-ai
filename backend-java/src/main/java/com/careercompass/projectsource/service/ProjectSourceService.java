package com.careercompass.projectsource.service;

import java.time.Clock;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceRequest;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSourceService {

    private final GitHubRepositoryVerificationService verificationService;
    private final ProjectSourceRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ProjectSourceService(
            GitHubRepositoryVerificationService verificationService,
            ProjectSourceRepository repository,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.verificationService = verificationService;
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /**
     * 기능: 현재 사용자가 선택한 공개 GitHub 저장소를 검증하고 현재 버전을 등록한다.
     * 반환 값: 등록 식별자, 정규 저장소 주소, 기본 브랜치, 커밋 식별자와 상태를 반환한다.
     */
    @Transactional
    public CreateGitHubProjectSourceResponse createGitHubProjectSource(
            CreateGitHubProjectSourceRequest request
    ) {
        VerifiedGitHubRepository verifiedRepository =
                verificationService.verifyRepository(request.repositoryUrl());
        ProjectSource projectSource = ProjectSource.create(
                UUID.randomUUID(),
                currentUserProvider.getCurrentUserId(),
                verifiedRepository.canonicalUrl().toString(),
                verifiedRepository.owner() + "/" + verifiedRepository.repository(),
                verifiedRepository.defaultBranch(),
                verifiedRepository.commitSha(),
                clock.instant()
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
