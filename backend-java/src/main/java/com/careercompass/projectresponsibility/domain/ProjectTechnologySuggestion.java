package com.careercompass.projectresponsibility.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTag;
import jakarta.persistence.*;

@Entity
@Table(name = "project_technology_suggestion")
public class ProjectTechnologySuggestion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_task_id", nullable = false)
    private ProjectResponsibilityExtractionTask extractionTask;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technology_tag_id", nullable = false)
    private TechnologyTag technologyTag;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 30)
    private ProjectTechnologySuggestionStatus decisionStatus;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @OneToMany(mappedBy = "suggestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("evidenceId ASC")
    private List<ProjectTechnologySuggestionEvidence> sourceEvidence = new ArrayList<>();

    protected ProjectTechnologySuggestion() {}

    public static ProjectTechnologySuggestion create(
            UUID id, ProjectResponsibilityExtractionTask extractionTask,
            TechnologyTag technologyTag, Instant createdAt, Instant expiresAt) {
        ProjectTechnologySuggestion suggestion = new ProjectTechnologySuggestion();
        suggestion.id = id;
        suggestion.extractionTask = extractionTask;
        suggestion.technologyTag = technologyTag;
        suggestion.decisionStatus = ProjectTechnologySuggestionStatus.PENDING;
        suggestion.createdAt = createdAt;
        suggestion.expiresAt = expiresAt;
        return suggestion;
    }

    public void addSourceEvidence(ProjectTechnologySuggestionEvidence evidence) {
        sourceEvidence.add(evidence);
    }

    public void add(Instant now) {
        requirePending();
        decisionStatus = ProjectTechnologySuggestionStatus.ADDED;
        decidedAt = now;
    }

    public void ignore(Instant now) {
        requirePending();
        decisionStatus = ProjectTechnologySuggestionStatus.IGNORED;
        decidedAt = now;
    }

    private void requirePending() {
        if (decisionStatus != ProjectTechnologySuggestionStatus.PENDING) {
            throw new IllegalStateException("PROJECT_TECHNOLOGY_SUGGESTION_FINAL");
        }
    }

    public UUID getId() { return id; }
    public TechnologyTag getTechnologyTag() { return technologyTag; }
    public ProjectTechnologySuggestionStatus getDecisionStatus() { return decisionStatus; }
    public long getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public List<ProjectTechnologySuggestionEvidence> getSourceEvidence() {
        return List.copyOf(sourceEvidence);
    }
}
