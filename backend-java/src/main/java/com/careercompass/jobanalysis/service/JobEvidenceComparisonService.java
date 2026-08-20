package com.careercompass.jobanalysis.service;

import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.pythonworker.client.PythonEvidenceSimilarityClient;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityEnvelope;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityException;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class JobEvidenceComparisonService {

    private static final Logger log =
            LoggerFactory.getLogger(JobEvidenceComparisonService.class);

    private final JobAnalysisExecutionService jobAnalysisExecutionService;
    private final PythonEvidenceSimilarityClient pythonEvidenceSimilarityClient;
    private final JobAnalysisJsonCodec jobAnalysisJsonCodec;

    /**
     * 기능: 저장된 공고 담당 업무와 확정된 사용자 프로젝트 담당 업무를 공고별로 비교하고 저장한다.
     * 반환 값: 없음.
     */
    public void compare(JobAnalysis jobAnalysis) {
        UUID jobAnalysisId = jobAnalysis.getId();
        List<JobAnalysisPosting> postings =
                jobAnalysisExecutionService.listPostings(jobAnalysisId);
        List<PythonEvidenceSimilarityRequest.UserEvidence> userEvidence =
                toUserEvidence(jobAnalysisExecutionService.listConfirmedResponsibilities(jobAnalysis));
        int successfulCallCount = 0;
        int failedCount = 0;
        JobAnalysisFailureCode firstFailureCode = null;

        for (JobAnalysisPosting posting : postings) {
            UUID comparisonTaskId = UUID.randomUUID();
            JobPostingComparisonSnapshot snapshot;
            try {
                List<PythonEvidenceSimilarityRequest.JobEvidence> jobEvidence =
                        jobAnalysisJsonCodec.parseJobEvidence(
                                posting.getExtractionJson());
                if (jobEvidence.isEmpty()) {
                    snapshot = JobPostingComparisonSnapshot.jobEvidenceUnavailable(
                            comparisonTaskId.toString(),
                            jobAnalysisId.toString(),
                            posting.getJobPostingId().toString()
                    );
                } else if (userEvidence.isEmpty()) {
                    snapshot = JobPostingComparisonSnapshot.userEvidenceUnavailable(
                            comparisonTaskId.toString(),
                            jobAnalysisId.toString(),
                            posting.getJobPostingId().toString(),
                            jobEvidence
                    );
                } else {
                    PythonEvidenceSimilarityRequest request =
                            new PythonEvidenceSimilarityRequest(
                                    comparisonTaskId.toString(),
                                    jobAnalysisId.toString(),
                                    posting.getJobPostingId().toString(),
                                    jobEvidence,
                                    userEvidence
                            );
                    PythonEvidenceSimilarityEnvelope.Data response =
                            pythonEvidenceSimilarityClient.compare(request);
                    snapshot = JobPostingComparisonSnapshot.fromPython(response);
                    successfulCallCount++;
                }
            } catch (PythonEvidenceSimilarityException exception) {
                JobAnalysisFailureCode failureCode = mapFailure(exception.getFailure());
                firstFailureCode = firstFailureCode == null ? failureCode : firstFailureCode;
                failedCount++;
                snapshot = JobPostingComparisonSnapshot.failed(
                        comparisonTaskId.toString(),
                        jobAnalysisId.toString(),
                        posting.getJobPostingId().toString(),
                        failureCode.name()
                );
                log.warn(
                        "job_evidence_comparison_failed jobAnalysisId={} jobPostingId={} failure={}",
                        jobAnalysisId,
                        posting.getJobPostingId(),
                        exception.getFailure(),
                        exception
                );
            } catch (JsonProcessingException exception) {
                JobAnalysisFailureCode failureCode =
                        JobAnalysisFailureCode.EVIDENCE_COMPARISON_INVALID_RESPONSE;
                firstFailureCode = firstFailureCode == null ? failureCode : firstFailureCode;
                failedCount++;
                snapshot = JobPostingComparisonSnapshot.failed(
                        comparisonTaskId.toString(),
                        jobAnalysisId.toString(),
                        posting.getJobPostingId().toString(),
                        failureCode.name()
                );
                log.warn(
                        "job_evidence_input_invalid jobAnalysisId={} jobPostingId={}",
                        jobAnalysisId,
                        posting.getJobPostingId(),
                        exception
                );
            }
            jobAnalysisExecutionService.recordPostingComparison(
                    jobAnalysisId,
                    posting.getId(),
                    serialize(snapshot)
            );
        }

        jobAnalysisExecutionService.finishEvidenceComparison(
                jobAnalysisId,
                postings.size() - failedCount,
                postings.size(),
                successfulCallCount,
                firstFailureCode
        );
    }

    private List<PythonEvidenceSimilarityRequest.UserEvidence> toUserEvidence(
            List<ConfirmedProjectResponsibility> responsibilities
    ) {
        return responsibilities.stream()
                .filter(responsibility -> responsibility.text() != null
                        && !responsibility.text().isBlank())
                .map(responsibility -> new PythonEvidenceSimilarityRequest.UserEvidence(
                        responsibility.evidenceId().toString(),
                        responsibility.projectSourceId().toString(),
                        "PROJECT_RESPONSIBILITY",
                        responsibility.text().strip()
                ))
                .toList();
    }

    private JobAnalysisFailureCode mapFailure(PythonEvidenceSimilarityFailure failure) {
        return failure == PythonEvidenceSimilarityFailure.MODEL_UNAVAILABLE
                ? JobAnalysisFailureCode.EVIDENCE_COMPARISON_MODEL_UNAVAILABLE
                : JobAnalysisFailureCode.EVIDENCE_COMPARISON_INVALID_RESPONSE;
    }

    private String serialize(JobPostingComparisonSnapshot snapshot) {
        try {
            return jobAnalysisJsonCodec.serialize(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("COMPARISON_SERIALIZATION_FAILED", exception);
        }
    }
}
