package com.careercompass.jobanalysis.repository;

import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAnalysisPostingRepository extends JpaRepository<JobAnalysisPosting, UUID> {

    List<JobAnalysisPosting> findByJobAnalysisIdOrderByCreatedAtAsc(UUID jobAnalysisId);
}
