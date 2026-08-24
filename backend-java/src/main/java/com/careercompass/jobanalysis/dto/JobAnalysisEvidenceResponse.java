package com.careercompass.jobanalysis.dto;

import java.util.UUID;

public record JobAnalysisEvidenceResponse(
        String evidenceId,
        String sourceType,
        UUID sourceId,
        String excerpt
) {
}
