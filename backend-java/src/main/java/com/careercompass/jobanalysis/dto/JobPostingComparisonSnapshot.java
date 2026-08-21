package com.careercompass.jobanalysis.dto;

import java.util.List;

import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityEnvelope;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;

public record JobPostingComparisonSnapshot(
        String comparisonTaskId,
        String jobAnalysisId,
        String jobPostingId,
        String status,
        String method,
        List<Result> results,
        ModelExecution modelExecution,
        String unavailableReason,
        String failureCode
) {
    public static JobPostingComparisonSnapshot fromPython(
            PythonEvidenceSimilarityEnvelope.Data similarityResponse
    ) {
        return new JobPostingComparisonSnapshot(
                similarityResponse.comparisonTaskId(),
                similarityResponse.jobAnalysisId(),
                similarityResponse.jobPostingId(),
                similarityResponse.status(),
                similarityResponse.method(),
                similarityResponse.results().stream().map(result -> new Result(
                        result.jobEvidenceId(),
                        result.status(),
                        result.bestMatchUserEvidenceId(),
                        result.score(),
                        result.judgment(),
                        result.unavailableReason()
                )).toList(),
                new ModelExecution(
                        similarityResponse.modelExecution().stage(),
                        similarityResponse.modelExecution().provider(),
                        similarityResponse.modelExecution().model()
                ),
                null,
                null
        );
    }

    public static JobPostingComparisonSnapshot jobEvidenceUnavailable(
            String comparisonTaskId,
            String jobAnalysisId,
            String jobPostingId
    ) {
        return new JobPostingComparisonSnapshot(
                comparisonTaskId,
                jobAnalysisId,
                jobPostingId,
                "NOT_CALCULABLE",
                null,
                List.of(),
                null,
                "JOB_EVIDENCE_EMPTY_AFTER_SANITIZATION",
                null
        );
    }

    public static JobPostingComparisonSnapshot userEvidenceUnavailable(
            String comparisonTaskId,
            String jobAnalysisId,
            String jobPostingId,
            List<PythonEvidenceSimilarityRequest.JobEvidence> jobEvidence
    ) {
        return new JobPostingComparisonSnapshot(
                comparisonTaskId,
                jobAnalysisId,
                jobPostingId,
                "NOT_CALCULABLE",
                null,
                jobEvidence.stream().map(evidence -> new Result(
                        evidence.evidenceId(),
                        "NOT_CALCULABLE",
                        null,
                        null,
                        null,
                        "USER_EVIDENCE_EMPTY_AFTER_SANITIZATION"
                )).toList(),
                null,
                null,
                null
        );
    }

    public static JobPostingComparisonSnapshot failed(
            String comparisonTaskId,
            String jobAnalysisId,
            String jobPostingId,
            String failureCode
    ) {
        return new JobPostingComparisonSnapshot(
                comparisonTaskId,
                jobAnalysisId,
                jobPostingId,
                "FAILED",
                null,
                List.of(),
                null,
                null,
                failureCode
        );
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

    public record ModelExecution(String stage, String provider, String model) {
    }
}
