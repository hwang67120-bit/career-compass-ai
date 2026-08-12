package com.careercompass.projectresponsibility.repository;

import java.util.Optional;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.ProjectResponsibilityExtractionTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ProjectResponsibilityExtractionTaskRepository
        extends JpaRepository<ProjectResponsibilityExtractionTask, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from ProjectResponsibilityExtractionTask task "
            + "where exists (select candidate.id from ProjectResponsibilityCandidate candidate "
            + "where candidate.extractionTask = task and candidate.id = :candidateId)")
    Optional<ProjectResponsibilityExtractionTask> findByCandidateIdForUpdate(UUID candidateId);

    Optional<ProjectResponsibilityExtractionTask>
    findFirstByProjectSource_IdOrderByCreatedAtDesc(UUID projectSourceId);
}
