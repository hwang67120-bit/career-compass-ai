package com.careercompass.projectresponsibility.repository;

import java.util.List;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.ProjectTechnologyFinding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTechnologyFindingRepository extends JpaRepository<ProjectTechnologyFinding, UUID> {
    @EntityGraph(attributePaths = {"technologyTag", "sourceEvidence"})
    List<ProjectTechnologyFinding> findAllByExtractionTask_IdOrderByTechnologyTag_DisplayNameAsc(UUID taskId);
}
