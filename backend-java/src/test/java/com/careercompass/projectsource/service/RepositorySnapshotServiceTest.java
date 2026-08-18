package com.careercompass.projectsource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.careercompass.projectsource.client.GitHubRepositoryBlob;
import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRepositoryTree;
import com.careercompass.projectsource.config.RepositorySnapshotPolicyProperties;
import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.projectsource.exception.RepositorySnapshotException;
import com.careercompass.projectsource.exception.RepositorySnapshotFailure;
import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RepositorySnapshotServiceTest {

    private static final String COMMIT_SHA =
            "0123456789abcdef0123456789abcdef01234567";

    private GitHubRepositoryGateway repositoryGateway;
    private RepositorySnapshotService service;
    private ProjectSource projectSource;
    private GitHubRepositoryCoordinates coordinates;

    @BeforeEach
    void setUp() {
        repositoryGateway = Mockito.mock(GitHubRepositoryGateway.class);
        RepositorySnapshotPolicyProperties snapshotPolicy =
                new RepositorySnapshotPolicyProperties(
                        102400,
                        3,
                        List.of(".git", "build", "node_modules"),
                        List.of(".env", "credential", "secret"),
                        List.of("pom.xml", "package.json"),
                        List.of("application.yml", "Dockerfile"),
                        List.of(".java", ".ts"));
        ProjectResponsibilityExtractionPolicyProperties extractionPolicy =
                new ProjectResponsibilityExtractionPolicyProperties(
                        10, 30, 3, 20, 10, 30, 2000, 20000, 500);
        service = new RepositorySnapshotService(
                repositoryGateway,
                snapshotPolicy,
                extractionPolicy,
                Clock.fixed(Instant.parse("2026-08-17T01:00:00Z"), ZoneOffset.UTC));
        projectSource = ProjectSource.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://github.com/example/sample",
                "example/sample",
                "main",
                COMMIT_SHA,
                Instant.parse("2026-08-17T00:00:00Z"));
        coordinates = GitHubRepositoryCoordinates.createFromUrl(
                projectSource.getRepositoryUrl());
    }

    @Test
    void prepare_withFixedCommitFiles_returnsLimitedSnapshotAndEvidenceMap() {
        List<GitHubRepositoryTree.Entry> entries = List.of(
                entry("src/main/java/example/App.java", "source-sha", "class App {}"),
                entry("src/test/java/example/AppTest.java", "test-sha", "class AppTest {}"),
                entry("pom.xml", "manifest-sha", "<project/>"),
                entry("application.yml", "config-sha", "spring: {}"),
                entry("README.md", "readme-sha", "Sample project"),
                entry(".env", "secret-sha", "TOKEN=value"));
        when(repositoryGateway.fetchTree(coordinates, COMMIT_SHA))
                .thenReturn(new GitHubRepositoryTree(entries, false));
        registerBlobs(entries);

        PreparedRepositorySnapshot prepared = service.prepare(projectSource, 1);

        assertThat(prepared.requestSnapshot().repositoryVersion()).isEqualTo(COMMIT_SHA);
        assertThat(prepared.requestSnapshot().fetchedAt())
                .isEqualTo(Instant.parse("2026-08-17T01:00:00Z"));
        assertThat(prepared.requestSnapshot().readmes()).hasSize(1);
        assertThat(prepared.requestSnapshot().files())
                .extracting(file -> file.fileType())
                .containsExactly("SOURCE", "TEST", "MANIFEST", "CONFIGURATION");
        assertThat(prepared.evidenceById()).hasSize(5);
        verify(repositoryGateway, never()).fetchBlob(coordinates, "secret-sha");
    }

    @Test
    void prepare_withTruncatedTree_rejectsSnapshotWithoutFetchingBlobs() {
        when(repositoryGateway.fetchTree(coordinates, COMMIT_SHA))
                .thenReturn(new GitHubRepositoryTree(List.of(), true));

        assertThatThrownBy(() -> service.prepare(projectSource, 1))
                .isInstanceOfSatisfying(
                        RepositorySnapshotException.class,
                        exception -> assertThat(exception.getFailure())
                                .isEqualTo(RepositorySnapshotFailure.TREE_TRUNCATED));
    }

    @Test
    void prepare_withOversizedFile_excludesItAndKeepsEligibleEvidence() {
        GitHubRepositoryTree.Entry oversized =
                new GitHubRepositoryTree.Entry(
                        "src/main/java/example/Large.java",
                        "blob",
                        "large-sha",
                        102401);
        GitHubRepositoryTree.Entry manifest =
                entry("pom.xml", "manifest-sha", "<project/>");
        when(repositoryGateway.fetchTree(coordinates, COMMIT_SHA))
                .thenReturn(new GitHubRepositoryTree(
                        List.of(oversized, manifest), false));
        when(repositoryGateway.fetchBlob(coordinates, manifest.sha()))
                .thenReturn(blob("<project/>"));

        PreparedRepositorySnapshot prepared = service.prepare(projectSource, 1);

        assertThat(prepared.requestSnapshot().files()).hasSize(1);
        assertThat(prepared.exclusions())
                .containsExactly(new PreparedRepositorySnapshot.Exclusion(
                        oversized.path(), "FILE_SIZE_LIMIT_EXCEEDED"));
        verify(repositoryGateway, never()).fetchBlob(coordinates, oversized.sha());
    }

    private GitHubRepositoryTree.Entry entry(
            String path,
            String sha,
            String text
    ) {
        return new GitHubRepositoryTree.Entry(
                path,
                "blob",
                sha,
                text.getBytes(StandardCharsets.UTF_8).length);
    }

    private void registerBlobs(List<GitHubRepositoryTree.Entry> entries) {
        for (GitHubRepositoryTree.Entry entry : entries) {
            if (!entry.path().equals(".env")) {
                String text = switch (entry.sha()) {
                    case "source-sha" -> "class App {}";
                    case "test-sha" -> "class AppTest {}";
                    case "manifest-sha" -> "<project/>";
                    case "config-sha" -> "spring: {}";
                    case "readme-sha" -> "Sample project";
                    default -> throw new IllegalArgumentException(entry.sha());
                };
                when(repositoryGateway.fetchBlob(coordinates, entry.sha()))
                        .thenReturn(blob(text));
            }
        }
    }

    private GitHubRepositoryBlob blob(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new GitHubRepositoryBlob(
                Base64.getEncoder().encodeToString(bytes),
                "base64",
                bytes.length);
    }
}
