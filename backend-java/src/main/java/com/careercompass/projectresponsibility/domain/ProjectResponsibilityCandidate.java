package com.careercompass.projectresponsibility.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "project_responsibility_candidate")
public class ProjectResponsibilityCandidate {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_task_id", nullable = false)
    private ProjectResponsibilityExtractionTask extractionTask;
    @Column(name = "extracted_text", length = 500) private String extractedText;
    @Column(name = "confirmed_text", length = 500) private String confirmedText;
    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_status", nullable = false, length = 30)
    private ProjectResponsibilityCandidateStatus candidateStatus;
    @Version @Column(name = "lock_version", nullable = false) private long lockVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("evidenceId ASC")
    private List<ProjectResponsibilityEvidence> sourceEvidence = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "project_responsibility_candidate_technology",
            joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "technology_tag_id", nullable = false)
    private Set<UUID> technologyTagIds = new LinkedHashSet<>();

    protected ProjectResponsibilityCandidate() {}

    public static ProjectResponsibilityCandidate create(
            UUID id, ProjectResponsibilityExtractionTask task, String text,
            Set<UUID> technologyTagIds, Instant createdAt, Instant expiresAt) {
        ProjectResponsibilityCandidate candidate = new ProjectResponsibilityCandidate();
        candidate.id = id;
        candidate.extractionTask = task;
        candidate.extractedText = text;
        candidate.candidateStatus = ProjectResponsibilityCandidateStatus.UNCONFIRMED;
        candidate.technologyTagIds.addAll(technologyTagIds);
        candidate.createdAt = createdAt;
        candidate.expiresAt = expiresAt;
        return candidate;
    }

    public void addSourceEvidence(ProjectResponsibilityEvidence evidence) {
        sourceEvidence.add(evidence);
    }

    public void confirm(String text, Instant now) {
        requireUnconfirmed();
        confirmedText = text;
        candidateStatus = ProjectResponsibilityCandidateStatus.CONFIRMED;
        decidedAt = now;
    }

    public void reject(Instant now) {
        requireUnconfirmed();
        extractedText = null;
        confirmedText = null;
        sourceEvidence.clear();
        candidateStatus = ProjectResponsibilityCandidateStatus.REJECTED;
        decidedAt = now;
    }

    private void requireUnconfirmed() {
        if (candidateStatus != ProjectResponsibilityCandidateStatus.UNCONFIRMED) {
            throw new IllegalStateException("PROJECT_RESPONSIBILITY_CANDIDATE_FINAL");
        }
    }

    public UUID getId() { return id; }
    public ProjectResponsibilityExtractionTask getExtractionTask() { return extractionTask; }
    public String getExtractedText() { return extractedText; }
    public String getConfirmedText() { return confirmedText; }
    public ProjectResponsibilityCandidateStatus getCandidateStatus() { return candidateStatus; }
    public long getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Set<UUID> getTechnologyTagIds() { return Set.copyOf(technologyTagIds); }
    public List<ProjectResponsibilityEvidence> getSourceEvidence() {
        return List.copyOf(sourceEvidence);
    }
}
