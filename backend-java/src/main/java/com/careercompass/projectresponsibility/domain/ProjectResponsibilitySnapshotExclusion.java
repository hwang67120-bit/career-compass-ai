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
@Table(name = "project_responsibility_snapshot_exclusion")
public class ProjectResponsibilitySnapshotExclusion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_task_id", nullable = false)
    private ProjectResponsibilityExtractionTask extractionTask;

    @Column(name = "file_path", nullable = false, columnDefinition = "text")
    private String filePath;

    @Column(name = "exclusion_reason", nullable = false, length = 80)
    private String exclusionReason;

    protected ProjectResponsibilitySnapshotExclusion() {
    }

    public static ProjectResponsibilitySnapshotExclusion create(
            UUID id,
            ProjectResponsibilityExtractionTask extractionTask,
            String filePath,
            String exclusionReason
    ) {
        ProjectResponsibilitySnapshotExclusion exclusion =
                new ProjectResponsibilitySnapshotExclusion();
        exclusion.id = id;
        exclusion.extractionTask = extractionTask;
        exclusion.filePath = filePath;
        exclusion.exclusionReason = exclusionReason;
        return exclusion;
    }
}
