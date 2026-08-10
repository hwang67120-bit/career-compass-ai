package com.careercompass.projectsource.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "github.api.connect-timeout=3s",
        "github.api.read-timeout=8s",
        "python.worker.internal-token=integration-test-token"
})
class ProjectSourceQueryRepositoryIntegrationTest {

    private static final UUID CURRENT_USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProjectSourceRepository projectSourceRepository;

    @Autowired
    private ProjectSourceQueryRepository projectSourceQueryRepository;

    @BeforeEach
    void clearProjectSources() {
        projectSourceRepository.deleteAll();
    }

    @Test
    void findAllByUserIdOrderByCreatedAtDescIdDesc_withMultipleUsers_returnsOnlyCurrentUsersSources() {
        ProjectSource olderCurrentUserSource = createProjectSource(
                "40000000-0000-0000-0000-000000000001",
                CURRENT_USER_ID,
                "octocat/older-repository",
                "2026-07-31T01:00:00Z"
        );
        ProjectSource newerCurrentUserSource = createProjectSource(
                "40000000-0000-0000-0000-000000000002",
                CURRENT_USER_ID,
                "octocat/newer-repository",
                "2026-07-31T02:00:00Z"
        );
        ProjectSource otherUserSource = createProjectSource(
                "40000000-0000-0000-0000-000000000003",
                OTHER_USER_ID,
                "other/private-to-current-user",
                "2026-07-31T03:00:00Z"
        );
        projectSourceRepository.saveAll(List.of(
                olderCurrentUserSource,
                newerCurrentUserSource,
                otherUserSource
        ));

        List<ProjectSource> projectSources =
                projectSourceQueryRepository
                        .findAllByUserIdOrderByCreatedAtDescIdDesc(CURRENT_USER_ID);

        assertThat(projectSources)
                .extracting(ProjectSource::getRepositoryFullName)
                .containsExactly(
                        "octocat/newer-repository",
                        "octocat/older-repository"
                );
    }

    private ProjectSource createProjectSource(
            String projectSourceId,
            UUID userId,
            String repositoryFullName,
            String createdAt
    ) {
        return ProjectSource.create(
                UUID.fromString(projectSourceId),
                userId,
                "https://github.com/" + repositoryFullName,
                repositoryFullName,
                "develop",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.parse(createdAt)
        );
    }
}
