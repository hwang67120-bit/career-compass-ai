package com.careercompass.jobanalysis.service.model;

import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;

public record EvidenceComparisonSummary(
        int completedPostingCount,
        int totalPostingCount,
        int successfulPythonCallCount,
        JobAnalysisFailureCode firstFailureCode
) {
}
