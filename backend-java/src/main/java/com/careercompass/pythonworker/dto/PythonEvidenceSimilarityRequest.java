package com.careercompass.pythonworker.dto;

import java.util.List;

public record PythonEvidenceSimilarityRequest(
        String comparisonTaskId,
        String jobAnalysisId,
        String jobPostingId,
        List<JobEvidence> jobEvidence,
        List<UserEvidence> userEvidence
) {
    public record JobEvidence(
            String evidenceId,
            String category,
            String text
    ) {
    }

    public record UserEvidence(
            String evidenceId,
            String projectSourceId,
            String category,
            String text
    ) {
    }
}
