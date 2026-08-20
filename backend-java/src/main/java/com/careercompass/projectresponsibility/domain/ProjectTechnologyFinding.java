package com.careercompass.projectresponsibility.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTag;
import jakarta.persistence.*;

@Entity
@Table(name = "project_technology_finding")
public class ProjectTechnologyFinding {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_task_id", nullable = false)
    private ProjectResponsibilityExtractionTask extractionTask;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technology_tag_id", nullable = false)
    private TechnologyTag technologyTag;
    @Enumerated(EnumType.STRING)
    @Column(name = "finding_status", nullable = false, length = 30)
    private ProjectTechnologyFindingStatus findingStatus;
    @OneToMany(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("evidenceId ASC")
    private List<ProjectTechnologyFindingEvidence> sourceEvidence = new ArrayList<>();

    protected ProjectTechnologyFinding() {}

    public static ProjectTechnologyFinding create(
            UUID id,
            ProjectResponsibilityExtractionTask extractionTask,
            TechnologyTag technologyTag,
            ProjectTechnologyFindingStatus findingStatus) {
        ProjectTechnologyFinding finding = new ProjectTechnologyFinding();
        finding.id = id;
        finding.extractionTask = extractionTask;
        finding.technologyTag = technologyTag;
        finding.findingStatus = findingStatus;
        return finding;
    }

    public void addSourceEvidence(ProjectTechnologyFindingEvidence evidence) {
        sourceEvidence.add(evidence);
    }

    public UUID getId() { return id; }
    public TechnologyTag getTechnologyTag() { return technologyTag; }
    public ProjectTechnologyFindingStatus getFindingStatus() { return findingStatus; }
    public List<ProjectTechnologyFindingEvidence> getSourceEvidence() {
        return List.copyOf(sourceEvidence);
    }
}
