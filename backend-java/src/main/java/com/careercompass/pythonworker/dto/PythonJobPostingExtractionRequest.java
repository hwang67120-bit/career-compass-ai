package com.careercompass.pythonworker.dto;

public record PythonJobPostingExtractionRequest(
        String jobPostingId,
        String extractionTaskId,
        String sourceText
) {
}
