package com.careercompass.jobanalysis.dto;

import java.util.UUID;

public record JobAnalysisResponse(
        UUID id,
        String analysisStatus,
        String currentStep,
        int completedUnits,
        int totalUnits,
        String failureCode
) {
}
