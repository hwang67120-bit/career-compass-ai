package com.careercompass.jobanalysis.dto;

import java.util.UUID;

public record JobAnalysisPostingResponse(
        UUID id,
        UUID jobPostingId,
        String providerPostingId,
        String provider,
        String companyName,
        String originalJobTitle,
        String sourceUrl,
        JobPostingComparisonSnapshot comparison
) {
}
