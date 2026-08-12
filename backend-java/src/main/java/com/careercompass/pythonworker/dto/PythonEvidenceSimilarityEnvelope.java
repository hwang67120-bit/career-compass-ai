package com.careercompass.pythonworker.dto;

import java.time.Instant;
import java.util.List;

public record PythonEvidenceSimilarityEnvelope(
        String requestId,
        Data data,
        Error error,
        Instant timestamp
) {
    public record Data(
            String comparisonTaskId,
            String jobAnalysisId,
            String jobPostingId,
            String status,
            String method,
            List<Result> results,
            ModelExecution modelExecution
    ) {
    }

    public record Result(
            String jobEvidenceId,
            String status,
            String bestMatchUserEvidenceId,
            Double score,
            String judgment,
            String unavailableReason
    ) {
    }

    public record ModelExecution(
            String stage,
            String provider,
            String model
    ) {
    }

    public record Error(
            String errorType,
            Boolean retryable
    ) {
    }
}
