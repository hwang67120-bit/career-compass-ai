package com.careercompass.projectsource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSourceStatus;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceRequest;
import com.careercompass.projectsource.dto.CreateGitHubProjectSourceResponse;
import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Transactional;

class ProjectSourceServiceTest {

    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");
    private static final String COMMIT_SHA =
            "0123456789abcdef0123456789abcdef01234567";

    private GitHubRepositoryVerificationService verificationService;
    private ProjectSourceRegistrationService registrationService;
    private ProjectSourceService projectSourceService;

    @BeforeEach
    void setUp() {
        verificationService = Mockito.mock(GitHubRepositoryVerificationService.class);
        registrationService = Mockito.mock(ProjectSourceRegistrationService.class);
        projectSourceService = new ProjectSourceService(
                verificationService,
                registrationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createGitHubProjectSource_withVerifiedRepository_registersVerifiedRepository() {
        VerifiedGitHubRepository verifiedRepository = new VerifiedGitHubRepository(
                "octocat",
                "Hello-World",
                URI.create("https://github.com/octocat/Hello-World"),
                "master",
                COMMIT_SHA
        );
        when(verificationService.verifyRepository("https://github.com/octocat/Hello-World"))
                .thenReturn(verifiedRepository);
        when(registrationService.createGitHubProjectSource(
                any(UUID.class), eq(verifiedRepository), eq(NOW)))
                .thenReturn(new CreateGitHubProjectSourceResponse(
                        PROJECT_SOURCE_ID,
                        "https://github.com/octocat/Hello-World",
                        "octocat/Hello-World",
                        "master",
                        COMMIT_SHA,
                        ProjectSourceStatus.REGISTERED
                ));

        CreateGitHubProjectSourceResponse response =
                projectSourceService.createGitHubProjectSource(
                        new CreateGitHubProjectSourceRequest(
                                "https://github.com/octocat/Hello-World"
                        )
                );

        verify(registrationService)
                .createGitHubProjectSource(any(UUID.class), eq(verifiedRepository), eq(NOW));
        assertThat(response.projectSourceId()).isEqualTo(PROJECT_SOURCE_ID);
    }

    @Test
    void createGitHubProjectSource_withInvalidUrl_rejectsBeforeSaving() {
        when(verificationService.verifyRepository("https://example.com/repository"))
                .thenThrow(new InvalidGitHubRepositoryUrlException());

        assertThatThrownBy(() -> projectSourceService.createGitHubProjectSource(
                new CreateGitHubProjectSourceRequest("https://example.com/repository")
        )).isInstanceOf(InvalidGitHubRepositoryUrlException.class);

        verifyNoInteractions(registrationService);
    }

    @Test
    void createGitHubProjectSource_methodDeclaration_hasNoTransactionalAnnotation()
            throws NoSuchMethodException {
        Method method = ProjectSourceService.class.getMethod(
                "createGitHubProjectSource",
                CreateGitHubProjectSourceRequest.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
    }
}
