package com.careercompass.projectresponsibility.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.careercompass.projectsource.domain.ProjectSource;
import com.careercompass.userprofile.domain.UserProfileVersion;
import jakarta.persistence.*;

@Entity
@Table(name = "project_responsibility_extraction_task")
public class ProjectResponsibilityExtractionTask {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_source_id", nullable = false)
    private ProjectSource projectSource;
    @Column(name = "linked_job_analysis_id") private UUID linkedJobAnalysisId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_user_profile_version_id", nullable = false)
    private UserProfileVersion baseUserProfileVersion;
    @Column(name = "repository_version", nullable = false, length = 40)
    private String repositoryVersion;
    @ElementCollection
    @CollectionTable(name = "project_responsibility_task_technology",
            joinColumns = @JoinColumn(name = "extraction_task_id"))
    @Column(name = "technology_tag_id", nullable = false)
    private Set<UUID> selectedTechnologyTagIds = new LinkedHashSet<>();
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 40)
    private ProjectResponsibilityExtractionStatus extractionStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 40)
    private ProjectResponsibilityReviewStatus reviewStatus;
    @ElementCollection
    @CollectionTable(name = "project_responsibility_failed_technology",
            joinColumns = @JoinColumn(name = "extraction_task_id"))
    @Column(name = "technology_tag_id", nullable = false)
    private Set<UUID> failedTechnologyTagIds = new LinkedHashSet<>();
    @Column(name = "failure_code", length = 80)
    private String failureCode;
    @Column(name = "model_provider", length = 30)
    private String modelProvider;
    @Column(name = "model_name", length = 200)
    private String modelName;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected ProjectResponsibilityExtractionTask() {}

    public static ProjectResponsibilityExtractionTask create(
            UUID id, ProjectSource source, UUID jobAnalysisId,
            UserProfileVersion baseVersion, Set<UUID> selectedTechnologyTagIds,
            Instant createdAt) {
        ProjectResponsibilityExtractionTask task = new ProjectResponsibilityExtractionTask();
        task.id = id;
        task.projectSource = source;
        task.linkedJobAnalysisId = jobAnalysisId;
        task.baseUserProfileVersion = baseVersion;
        task.repositoryVersion = source.getCommitSha();
        task.selectedTechnologyTagIds.addAll(selectedTechnologyTagIds);
        task.extractionStatus = ProjectResponsibilityExtractionStatus.EXTRACTING;
        task.reviewStatus = ProjectResponsibilityReviewStatus.AWAITING_USER_CONFIRMATION;
        task.createdAt = createdAt;
        return task;
    }

    public void completeExtraction(
            Set<UUID> failedTechnologyTagIds,
            String failureCode,
            String modelProvider,
            String modelName
    ) {
        if (extractionStatus != ProjectResponsibilityExtractionStatus.EXTRACTING) {
            throw new IllegalStateException("PROJECT_RESPONSIBILITY_EXTRACTION_FINAL");
        }
        this.failedTechnologyTagIds.addAll(failedTechnologyTagIds);
        this.failureCode = failureCode;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        extractionStatus = failedTechnologyTagIds.isEmpty()
                ? ProjectResponsibilityExtractionStatus.EXTRACTED
                : ProjectResponsibilityExtractionStatus.PARTIALLY_EXTRACTED;
    }

    public void failExtraction(String failureCode) {
        if (extractionStatus != ProjectResponsibilityExtractionStatus.EXTRACTING) {
            throw new IllegalStateException("PROJECT_RESPONSIBILITY_EXTRACTION_FINAL");
        }
        this.failureCode = failureCode;
        extractionStatus = ProjectResponsibilityExtractionStatus.FAILED;
    }

    public void completeReview(Instant reviewedAt) {
        if (reviewStatus != ProjectResponsibilityReviewStatus.AWAITING_USER_CONFIRMATION) {
            throw new IllegalStateException("PROJECT_RESPONSIBILITY_REVIEW_ALREADY_COMPLETED");
        }
        reviewStatus = ProjectResponsibilityReviewStatus.REVIEW_COMPLETED;
        this.reviewedAt = reviewedAt;
    }

    public UUID getId() { return id; }
    public ProjectSource getProjectSource() { return projectSource; }
    public UUID getLinkedJobAnalysisId() { return linkedJobAnalysisId; }
    public UserProfileVersion getBaseUserProfileVersion() { return baseUserProfileVersion; }
    public String getRepositoryVersion() { return repositoryVersion; }
    public Set<UUID> getSelectedTechnologyTagIds() { return Set.copyOf(selectedTechnologyTagIds); }
    public ProjectResponsibilityExtractionStatus getExtractionStatus() { return extractionStatus; }
    public ProjectResponsibilityReviewStatus getReviewStatus() { return reviewStatus; }
    public Set<UUID> getFailedTechnologyTagIds() {
        return Set.copyOf(failedTechnologyTagIds);
    }
    public String getFailureCode() { return failureCode; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
}
