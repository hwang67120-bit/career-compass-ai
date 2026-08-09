package com.careercompass.pythonworker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonJobPostingExtractionEnvelope(
        String requestId,
        Data data,
        Error error,
        String timestamp
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String jobPostingId,
            String extractionTaskId,
            String status,
            Object extraction,
            Object modelExecutions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
            String errorType,
            String message,
            boolean retryable
    ) {
    }
}
