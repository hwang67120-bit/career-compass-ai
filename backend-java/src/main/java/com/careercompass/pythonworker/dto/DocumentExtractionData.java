package com.careercompass.pythonworker.dto;

import java.util.UUID;

public record DocumentExtractionData(
        UUID documentId,
        UUID extractionTaskId,
        DocumentExtractionStatus status,
        ProfileCandidatePayload candidate,
        String modelProvider,
        String modelName,
        boolean piiRemoved
) {
}
