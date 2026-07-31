package com.careercompass.projectsource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceType;
import com.careercompass.projectsource.dto.ListProjectSourceResponse;
import com.careercompass.projectsource.repository.ProjectSourceQueryRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProjectSourceQueryServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant VERIFIED_AT =
            Instant.parse("2026-07-31T03:00:00Z");

    private ProjectSourceQueryRepository repository;
    private CurrentUserProvider currentUserProvider;
    private ProjectSourceQueryService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ProjectSourceQueryRepository.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        service = new ProjectSourceQueryService(repository, currentUserProvider);
    }

    @Test
    void listCurrentUserProjectSources_withRegisteredSources_returnsMappedSources() {
        ProjectSource projectSource = ProjectSource.create(
                PROJECT_SOURCE_ID,
                USER_ID,
                "https://github.com/octocat/Hello-World",
                "octocat/Hello-World",
                "master",
                "0123456789abcdef0123456789abcdef01234567",
                VERIFIED_AT
        );
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(projectSource));

        List<ListProjectSourceResponse> responses =
                service.listCurrentUserProjectSources();

        assertThat(responses).containsExactly(new ListProjectSourceResponse(
                PROJECT_SOURCE_ID,
                ProjectSourceType.GITHUB_PUBLIC_REPOSITORY,
                "octocat",
                "Hello-World",
                "master",
                VERIFIED_AT
        ));
        verify(repository).findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID);
    }

    @Test
    void listCurrentUserProjectSources_withoutRegisteredSources_returnsEmptyList() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of());

        assertThat(service.listCurrentUserProjectSources()).isEmpty();
        verify(repository).findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID);
    }
}
