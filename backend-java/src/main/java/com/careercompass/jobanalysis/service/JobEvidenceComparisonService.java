package com.careercompass.jobanalysis.service;

import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.jobanalysis.service.model.ConfirmedProjectResponsibilityEvidence;
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
     * 기능: 확정된 프로젝트 근거와 공고 담당 업무를 공고별로 비교하고 최종 분석 상태를 결정한다.
     * 반환 값: 없음.
     */
    public void compare(JobAnalysis jobAnalysis) {
        UUID jobAnalysisId = jobAnalysis.getId();
        List<JobAnalysisPosting> postings =
                jobAnalysisExecutionService.listPostings(jobAnalysisId);

        List<PythonEvidenceSimilarityRequest.UserEvidence> userEvidence =
                toUserEvidence(jobAnalysisExecutionService.listConfirmedResponsibilities(jobAnalysis));

        /*
         * ComparisonSummary(공고 비교 집계)
         * - 포함하는 값: 실패 없이 처리된 공고 수(비교 불가 포함), 성공한 Python 호출 수,
         *   처음 발생한 실패 코드.
         * - 생성 과정: compareAndRecordPostings가 공고별 근거를 비교하고 결과를 저장한 뒤 집계한다.
         * - 비교 방식: 양쪽 근거가 있으면 Python에 의미 비교를 요청하고, 어느 한쪽이 없으면
         *   Python을 호출하지 않고 비교 불가로 처리한다.
         * - 저장 여부: 이 객체 자체는 저장하지 않고, 포함된 값을 최종 분석 상태 결정에 사용한다.
         */
        ComparisonSummary comparisonSummary = compareAndRecordPostings(
                jobAnalysisId,
                postings,
                userEvidence);

        jobAnalysisExecutionService.finishEvidenceComparison(
                jobAnalysisId,
                comparisonSummary.completedPostingCount(),
                postings.size(),
                comparisonSummary.successfulPythonCallCount(),
                comparisonSummary.firstFailureCode()
        );
    }

    private ComparisonSummary compareAndRecordPostings(
            UUID jobAnalysisId,
            List<JobAnalysisPosting> postings,
            List<PythonEvidenceSimilarityRequest.UserEvidence> userEvidence
    ) {
        int completedPostingCount = 0;
        int successfulPythonCallCount = 0;
        JobAnalysisFailureCode firstFailureCode = null;

        for (JobAnalysisPosting posting : postings) {
            PostingComparisonOutcome outcome = compareAndRecordPosting(
                    jobAnalysisId,
                    posting,
                    userEvidence);
            if (outcome.failureCode() == null) {
                completedPostingCount++;
            } else if (firstFailureCode == null) {
                firstFailureCode = outcome.failureCode();
            }
            if (outcome.pythonCallSucceeded()) {
                successfulPythonCallCount++;
            }
        }

        return new ComparisonSummary(
                completedPostingCount,
                successfulPythonCallCount,
                firstFailureCode);
    }

    private PostingComparisonOutcome compareAndRecordPosting(
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            List<PythonEvidenceSimilarityRequest.UserEvidence> userEvidence
    ) {
        UUID comparisonTaskId = UUID.randomUUID();
        PostingComparisonOutcome outcome;
        try {
            outcome = createPostingComparison(
                    comparisonTaskId,
                    jobAnalysisId,
                    posting,
                    userEvidence);
        } catch (PythonEvidenceSimilarityException exception) {
            JobAnalysisFailureCode failureCode = mapFailure(exception.getFailure());
            logPythonComparisonFailure(jobAnalysisId, posting, exception);
            outcome = createFailedComparison(
                    comparisonTaskId,
                    jobAnalysisId,
                    posting,
                    failureCode);
        } catch (JsonProcessingException exception) {
            JobAnalysisFailureCode failureCode =
                    JobAnalysisFailureCode.EVIDENCE_COMPARISON_INVALID_RESPONSE;
            logInvalidJobEvidence(jobAnalysisId, posting, exception);
            outcome = createFailedComparison(
                    comparisonTaskId,
                    jobAnalysisId,
                    posting,
                    failureCode);
        }

        recordComparisonSnapshot(
                jobAnalysisId,
                posting,
                outcome.snapshot());
        return outcome;
    }

    private PostingComparisonOutcome createPostingComparison(
            UUID comparisonTaskId,
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            List<PythonEvidenceSimilarityRequest.UserEvidence> userEvidence
    ) throws JsonProcessingException {
        List<PythonEvidenceSimilarityRequest.JobEvidence> jobEvidence =
                jobAnalysisJsonCodec.parseJobEvidence(posting.getExtractionJson());

        if (jobEvidence.isEmpty()) {
            return PostingComparisonOutcome.completed(
                    JobPostingComparisonSnapshot.jobEvidenceUnavailable(
                            comparisonTaskId.toString(),
                            jobAnalysisId.toString(),
                            posting.getJobPostingId().toString()),
                    false);
        }
        if (userEvidence.isEmpty()) {
            return PostingComparisonOutcome.completed(
                    JobPostingComparisonSnapshot.userEvidenceUnavailable(
                            comparisonTaskId.toString(),
                            jobAnalysisId.toString(),
                            posting.getJobPostingId().toString(),
                            jobEvidence),
                    false);
        }

        PythonEvidenceSimilarityRequest request = new PythonEvidenceSimilarityRequest(
                comparisonTaskId.toString(),
                jobAnalysisId.toString(),
                posting.getJobPostingId().toString(),
                jobEvidence,
                userEvidence
        );
        PythonEvidenceSimilarityEnvelope.Data response =
                pythonEvidenceSimilarityClient.compare(request);
        return PostingComparisonOutcome.completed(
                JobPostingComparisonSnapshot.fromPython(response),
                true);
    }

    private PostingComparisonOutcome createFailedComparison(
            UUID comparisonTaskId,
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            JobAnalysisFailureCode failureCode
    ) {
        return PostingComparisonOutcome.failed(
                JobPostingComparisonSnapshot.failed(
                        comparisonTaskId.toString(),
                        jobAnalysisId.toString(),
                        posting.getJobPostingId().toString(),
                        failureCode.name()),
                failureCode);
    }

    private void recordComparisonSnapshot(
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            JobPostingComparisonSnapshot snapshot
    ) {
        jobAnalysisExecutionService.recordPostingComparison(
                jobAnalysisId,
                posting.getId(),
                serialize(snapshot)
        );
    }

    private void logPythonComparisonFailure(
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            PythonEvidenceSimilarityException exception
    ) {
        log.warn(
                "job_evidence_comparison_failed jobAnalysisId={} jobPostingId={} failure={}",
                jobAnalysisId,
                posting.getJobPostingId(),
                exception.getFailure(),
                exception
        );
    }

    private void logInvalidJobEvidence(
            UUID jobAnalysisId,
            JobAnalysisPosting posting,
            JsonProcessingException exception
    ) {
        log.warn(
                "job_evidence_input_invalid jobAnalysisId={} jobPostingId={}",
                jobAnalysisId,
                posting.getJobPostingId(),
                exception
        );
    }

    private List<PythonEvidenceSimilarityRequest.UserEvidence> toUserEvidence(
            List<ConfirmedProjectResponsibilityEvidence> responsibilities
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

    private record PostingComparisonOutcome(
            JobPostingComparisonSnapshot snapshot,
            boolean pythonCallSucceeded,
            JobAnalysisFailureCode failureCode
    ) {
        private static PostingComparisonOutcome completed(
                JobPostingComparisonSnapshot snapshot,
                boolean pythonCallSucceeded
        ) {
            return new PostingComparisonOutcome(
                    snapshot,
                    pythonCallSucceeded,
                    null);
        }

        private static PostingComparisonOutcome failed(
                JobPostingComparisonSnapshot snapshot,
                JobAnalysisFailureCode failureCode
        ) {
            return new PostingComparisonOutcome(snapshot, false, failureCode);
        }
    }

    private record ComparisonSummary(
            int completedPostingCount,
            int successfulPythonCallCount,
            JobAnalysisFailureCode firstFailureCode
    ) {
    }
}
