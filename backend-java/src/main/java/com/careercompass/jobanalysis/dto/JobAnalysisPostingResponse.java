package com.careercompass.jobanalysis.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record JobAnalysisPostingResponse(
        String providerPostingId,
        String companyName,
        String originalJobTitle,
        String sourceUrl,
        JsonNode extraction,
        JsonNode modelExecutions
) {
}
