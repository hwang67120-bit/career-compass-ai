package com.careercompass.projectsource.service;

import java.time.Clock;
import java.util.UUID;

import com.careercompass.projectsource.dto.CreateGitHubProjectSourceRequest;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import org.springframework.stereotype.Service;

@Service
public class ProjectSourceService {

    private final GitHubRepositoryVerificationService verificationService;
    private final ProjectSourceRegistrationService registrationService;
    private final Clock clock;

    public ProjectSourceService(
            GitHubRepositoryVerificationService verificationService,
            ProjectSourceRegistrationService registrationService,
            Clock clock
    ) {
        this.verificationService = verificationService;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    /**
     * 기능: 현재 사용자가 선택한 공개 GitHub 저장소를 트랜잭션 밖에서 검증하고 현재 버전을 등록한다.
     * 반환 값: 등록 식별자, 정규 저장소 주소, 기본 브랜치, 커밋 식별자와 상태를 반환한다.
     */
    public CreateGitHubProjectSourceResponse createGitHubProjectSource(
            CreateGitHubProjectSourceRequest request
    ) {
        VerifiedGitHubRepository verifiedRepository =
                verificationService.verifyRepository(request.repositoryUrl());
        return registrationService.createGitHubProjectSource(
                UUID.randomUUID(),
                verifiedRepository,
                clock.instant()
        );
    }
}
