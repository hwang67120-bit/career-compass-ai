package com.careercompass.pythonworker.dto;

import java.util.List;

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
            List<ModelExecution> modelExecutions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelExecution(
            String stage,
            String provider,
            String model
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
