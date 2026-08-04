package com.careercompass.jobanalysis.repository;

import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, UUID> {
}
