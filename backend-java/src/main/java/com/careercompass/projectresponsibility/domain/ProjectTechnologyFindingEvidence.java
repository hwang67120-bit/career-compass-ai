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
@Table(name = "project_technology_finding_evidence")
public class ProjectTechnologyFindingEvidence {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private ProjectTechnologyFinding finding;
    @Column(name = "evidence_id", nullable = false, length = 100) private String evidenceId;
    @Column(name = "file_path", nullable = false, columnDefinition = "text") private String filePath;
    @Column(name = "excerpt", nullable = false, length = 2000) private String excerpt;

    protected ProjectTechnologyFindingEvidence() {}

    public static ProjectTechnologyFindingEvidence create(
            UUID id, ProjectTechnologyFinding finding, String evidenceId,
            String filePath, String excerpt) {
        ProjectTechnologyFindingEvidence evidence = new ProjectTechnologyFindingEvidence();
        evidence.id = id;
        evidence.finding = finding;
        evidence.evidenceId = evidenceId;
        evidence.filePath = filePath;
        evidence.excerpt = excerpt;
        return evidence;
    }

    public String getEvidenceId() { return evidenceId; }
    public String getFilePath() { return filePath; }
    public String getExcerpt() { return excerpt; }
}
