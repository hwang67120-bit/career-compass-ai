package com.careercompass.jobanalysis.repository;

import java.util.Optional;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, UUID> {

    /**
     * 기능: 대기 중인 작업을 하나만 잠금 선점한다. 다른 워커가 이미 잠근 행은 건너뛴다
     * (docs/architecture/backend-job-processing-and-sse.md가 확정한 방식).
     * 호출부가 짧은 트랜잭션 안에서 호출해 즉시 상태를 바꾼 뒤 커밋해야 한다.
     */
    @Query(
            value = "SELECT * FROM job_analysis "
                    + "WHERE analysis_status = 'QUEUED' "
                    + "ORDER BY queued_at ASC "
                    + "LIMIT 1 "
                    + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true
    )
    Optional<JobAnalysis> findNextQueuedForUpdateSkipLocked();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select analysis from JobAnalysis analysis where analysis.id = :id")
    Optional<JobAnalysis> findByIdForUpdate(UUID id);
}
