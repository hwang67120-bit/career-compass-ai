package com.careercompass.projectresponsibility.domain;

import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "project_technology_suggestion_evidence")
public class ProjectTechnologySuggestionEvidence {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggestion_id", nullable = false)
    private ProjectTechnologySuggestion suggestion;
    @Column(name = "evidence_id", nullable = false, length = 100) private String evidenceId;
    @Column(name = "file_path", nullable = false, columnDefinition = "text") private String filePath;
    @Column(name = "excerpt", nullable = false, length = 2000) private String excerpt;

    protected ProjectTechnologySuggestionEvidence() {}

    public static ProjectTechnologySuggestionEvidence create(
            UUID id, ProjectTechnologySuggestion suggestion, String evidenceId,
            String filePath, String excerpt) {
        ProjectTechnologySuggestionEvidence evidence = new ProjectTechnologySuggestionEvidence();
        evidence.id = id;
        evidence.suggestion = suggestion;
        evidence.evidenceId = evidenceId;
        evidence.filePath = filePath;
        evidence.excerpt = excerpt;
        return evidence;
    }

    public String getEvidenceId() { return evidenceId; }
    public String getFilePath() { return filePath; }
    public String getExcerpt() { return excerpt; }
}
