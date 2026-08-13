package com.careercompass.pythonworker.dto;

import java.time.Instant;
import java.util.List;

public record PythonProjectResponsibilityExtractionEnvelope(
        String requestId, Data data, Error error, Instant timestamp
) {
    public record Data(
            String extractionTaskId, String projectSourceId, String repositoryVersion,
            List<DetectedTechnology> detectedTechnologies,
            List<ResponsibilityEvidenceCandidate> responsibilityEvidenceCandidates,
            ModelExecution modelExecution
    ) {
    }
    public record DetectedTechnology(
            String detectedName, String source, List<String> evidenceIds,
            String technologyTagId, String canonicalName, String findingStatus
    ) {
    }
    public record ResponsibilityEvidenceCandidate(
            String evidenceId, String category, String text,
            List<String> sourceEvidenceIds, String confirmationStatus
    ) {
    }
    public record ModelExecution(String stage, String provider, String model) {
    }
    public record Error(String errorType, Boolean retryable) {
    }
}
