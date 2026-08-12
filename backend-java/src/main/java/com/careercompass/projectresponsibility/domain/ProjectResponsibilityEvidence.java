package com.careercompass.projectresponsibility.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_responsibility_evidence")
public class ProjectResponsibilityEvidence {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private ProjectResponsibilityCandidate candidate;

    @Column(name = "evidence_id", nullable = false, length = 100)
    private String evidenceId;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "excerpt", nullable = false, length = 2000)
    private String excerpt;

    protected ProjectResponsibilityEvidence() {
    }

    public static ProjectResponsibilityEvidence create(
            UUID id,
            ProjectResponsibilityCandidate candidate,
            String evidenceId,
            String filePath,
            String excerpt
    ) {
        ProjectResponsibilityEvidence evidence = new ProjectResponsibilityEvidence();
        evidence.id = id;
        evidence.candidate = candidate;
        evidence.evidenceId = evidenceId;
        evidence.filePath = filePath;
        evidence.excerpt = excerpt;
        return evidence;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getExcerpt() {
        return excerpt;
    }
}
