package com.careercompass.projectsource.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_source")
public class ProjectSource {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "repository_url", nullable = false, columnDefinition = "text")
    private String repositoryUrl;

    @Column(name = "repository_full_name", nullable = false, columnDefinition = "text")
    private String repositoryFullName;

    @Column(name = "default_branch", nullable = false, columnDefinition = "text")
    private String defaultBranch;

    @Column(name = "commit_sha", nullable = false, columnDefinition = "text")
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_source_status", nullable = false, length = 30)
    private ProjectSourceStatus projectSourceStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectSource() {
    }

    private ProjectSource(UUID id, UUID userId, String repositoryUrl,
                          String repositoryFullName, String defaultBranch,
                          String commitSha, ProjectSourceStatus projectSourceStatus,
                          Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.repositoryUrl = repositoryUrl;
        this.repositoryFullName = repositoryFullName;
        this.defaultBranch = defaultBranch;
        this.commitSha = commitSha;
        this.projectSourceStatus = projectSourceStatus;
        this.createdAt = createdAt;
    }

    public static ProjectSource create(UUID id, UUID userId, String repositoryUrl,
                                       String repositoryFullName, String defaultBranch,
                                       String commitSha, Instant createdAt) {
        return new ProjectSource(
                id,
                userId,
                repositoryUrl,
                repositoryFullName,
                defaultBranch,
                commitSha,
                ProjectSourceStatus.REGISTERED,
                createdAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public ProjectSourceStatus getProjectSourceStatus() {
        return projectSourceStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
