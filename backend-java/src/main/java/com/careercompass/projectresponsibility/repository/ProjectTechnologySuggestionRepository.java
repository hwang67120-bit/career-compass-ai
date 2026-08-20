package com.careercompass.projectresponsibility.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.ProjectTechnologySuggestion;
import com.careercompass.projectresponsibility.domain.ProjectTechnologySuggestionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTechnologySuggestionRepository extends JpaRepository<ProjectTechnologySuggestion, UUID> {
    @EntityGraph(attributePaths = {"technologyTag", "sourceEvidence"})
    List<ProjectTechnologySuggestion> findAllByExtractionTask_IdOrderByCreatedAtAsc(UUID taskId);
    Optional<ProjectTechnologySuggestion> findByIdAndExtractionTask_Id(UUID id, UUID taskId);
    boolean existsByExtractionTask_IdAndDecisionStatus(UUID taskId, ProjectTechnologySuggestionStatus status);
    List<ProjectTechnologySuggestion> findAllByExtractionTask_IdAndDecisionStatusOrderByCreatedAtAsc(
            UUID taskId, ProjectTechnologySuggestionStatus status);
}
