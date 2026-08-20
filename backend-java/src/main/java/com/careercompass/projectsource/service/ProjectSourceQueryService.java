package com.careercompass.projectsource.service;

import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceType;
import com.careercompass.projectsource.dto.ListProjectSourceResponse;
import com.careercompass.projectsource.repository.ProjectSourceQueryRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectSourceQueryService {

    private final ProjectSourceQueryRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public ProjectSourceQueryService(
            ProjectSourceQueryRepository repository,
            CurrentUserProvider currentUserProvider
    ) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 기능: 현재 사용자가 등록한 공개 GitHub 프로젝트 출처를 최신 검증 순서로 조회한다.
     * 반환 값: 프로젝트 출처 식별자, 저장소 위치, 기본 브랜치와 마지막 검증 시각을 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ListProjectSourceResponse> listCurrentUserProjectSources() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        return repository.findAllByUserIdOrderByCreatedAtDescIdDesc(currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ListProjectSourceResponse toResponse(ProjectSource projectSource) {
        String[] repositoryCoordinates = projectSource.getRepositoryFullName().split("/", 2);
        if (repositoryCoordinates.length != 2) {
            throw new IllegalStateException("검증된 GitHub 저장소 식별자 형식이 올바르지 않습니다.");
        }
        return new ListProjectSourceResponse(
                projectSource.getId(),
                ProjectSourceType.GITHUB_PUBLIC_REPOSITORY,
                repositoryCoordinates[0],
                repositoryCoordinates[1],
                projectSource.getDefaultBranch(),
                projectSource.getCreatedAt()
        );
    }
}
