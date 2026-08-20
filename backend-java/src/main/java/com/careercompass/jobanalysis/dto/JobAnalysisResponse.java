package com.careercompass.jobanalysis.dto;

import java.util.List;
import java.util.UUID;

public record JobAnalysisResponse(
        UUID id,
        String analysisStatus,
        String currentStep,
        int completedUnits,
        int totalUnits,
        String failureCode,
        List<JobAnalysisPostingResponse> postings
) {
}
