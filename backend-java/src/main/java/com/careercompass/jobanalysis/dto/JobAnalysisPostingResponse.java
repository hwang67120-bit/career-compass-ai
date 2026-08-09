package com.careercompass.jobanalysis.dto;

public record JobAnalysisPostingResponse(
        String providerPostingId,
        String provider,
        String companyName,
        String originalJobTitle,
        String sourceUrl,
        Object extraction,
        Object modelExecutions
) {
}
