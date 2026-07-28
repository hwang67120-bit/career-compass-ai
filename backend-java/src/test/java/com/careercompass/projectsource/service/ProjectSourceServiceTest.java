package com.careercompass.projectsource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceRequest;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
import com.careercompass.projectsource.repository.ProjectSourceRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProjectSourceServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");
    private static final String COMMIT_SHA =
            "0123456789abcdef0123456789abcdef01234567";

    private GitHubRepositoryVerificationService verificationService;
    private ProjectSourceRepository repository;
    private CurrentUserProvider currentUserProvider;
    private ProjectSourceService projectSourceService;

    @BeforeEach
    void setUp() {
        verificationService = Mockito.mock(GitHubRepositoryVerificationService.class);
        repository = Mockito.mock(ProjectSourceRepository.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(repository.save(any(ProjectSource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        projectSourceService = new ProjectSourceService(
                verificationService,
                repository,
                currentUserProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createGitHubProjectSource_withVerifiedRepository_savesCurrentUsersSource() {
        when(verificationService.verifyRepository("https://github.com/octocat/Hello-World"))
                .thenReturn(new VerifiedGitHubRepository(
                        "octocat",
                        "Hello-World",
                        URI.create("https://github.com/octocat/Hello-World"),
                        "master",
                        COMMIT_SHA
                ));

        CreateGitHubProjectSourceResponse response =
                projectSourceService.createGitHubProjectSource(
                        new CreateGitHubProjectSourceRequest(
                                "https://github.com/octocat/Hello-World"
                        )
                );

        ArgumentCaptor<ProjectSource> captor =
                ArgumentCaptor.forClass(ProjectSource.class);
        verify(repository).save(captor.capture());
        ProjectSource savedProjectSource = captor.getValue();
        assertThat(savedProjectSource.getUserId()).isEqualTo(USER_ID);
        assertThat(savedProjectSource.getRepositoryUrl())
                .isEqualTo("https://github.com/octocat/Hello-World");
        assertThat(savedProjectSource.getRepositoryFullName())
                .isEqualTo("octocat/Hello-World");
        assertThat(savedProjectSource.getDefaultBranch()).isEqualTo("master");
        assertThat(savedProjectSource.getCommitSha()).isEqualTo(COMMIT_SHA);
        assertThat(savedProjectSource.getProjectSourceStatus())
                .isEqualTo(ProjectSourceStatus.REGISTERED);
        assertThat(savedProjectSource.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.projectSourceId()).isEqualTo(savedProjectSource.getId());
        assertThat(response.status()).isEqualTo(ProjectSourceStatus.REGISTERED);
    }

    @Test
    void createGitHubProjectSource_withInvalidUrl_rejectsBeforeSaving() {
        when(verificationService.verifyRepository("https://example.com/repository"))
                .thenThrow(new InvalidGitHubRepositoryUrlException());

        assertThatThrownBy(() -> projectSourceService.createGitHubProjectSource(
                new CreateGitHubProjectSourceRequest("https://example.com/repository")
        )).isInstanceOf(InvalidGitHubRepositoryUrlException.class);

        verifyNoInteractions(repository, currentUserProvider);
    }
}
