package com.careercompass.projectresponsibility.repository;

import java.util.UUID;

import com.careercompass.projectresponsibility.domain.ProjectResponsibilitySnapshotExclusion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectResponsibilitySnapshotExclusionRepository
        extends JpaRepository<ProjectResponsibilitySnapshotExclusion, UUID> {
}
