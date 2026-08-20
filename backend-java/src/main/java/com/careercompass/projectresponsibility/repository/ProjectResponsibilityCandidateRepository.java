package com.careercompass.projectresponsibility.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityCandidate;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityCandidateStatus;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectResponsibilityCandidateRepository
        extends JpaRepository<ProjectResponsibilityCandidate, UUID> {
    @EntityGraph(attributePaths = "technologyTagIds")
    List<ProjectResponsibilityCandidate> findAllByExtractionTask_IdOrderByCreatedAtAsc(UUID taskId);
    Optional<ProjectResponsibilityCandidate> findByIdAndExtractionTask_Id(UUID id, UUID taskId);
    boolean existsByExtractionTask_IdAndCandidateStatus(
            UUID taskId, ProjectResponsibilityCandidateStatus status);
    List<ProjectResponsibilityCandidate> findAllByExtractionTask_IdAndCandidateStatusOrderByCreatedAtAsc(
            UUID taskId, ProjectResponsibilityCandidateStatus status);
}
