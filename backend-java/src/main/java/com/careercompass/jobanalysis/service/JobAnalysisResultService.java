package com.careercompass.jobanalysis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.dto.JobAnalysisEvidenceResponse;
import com.careercompass.jobanalysis.dto.JobAnalysisPostingResponse;
import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.service.model.ConfirmedProjectResponsibilityEvidence;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class JobAnalysisResultService {

    private final JobAnalysisPostingRepository jobAnalysisPostingRepository;
    private final JobAnalysisExecutionService jobAnalysisExecutionService;
    private final JobAnalysisJsonCodec jobAnalysisJsonCodec;

    /**
     * 기능: 분석 작업에 저장된 공고 메타데이터와 의미 비교 결과를 사용자 응답 형태로 조회한다.
     * 반환 값: 저장 순서대로 정렬된 공고별 비교 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisPostingResponse> listPostingResults(
            UUID jobAnalysisId
    ) {
        return listPostings(jobAnalysisId).stream()
                .map(posting -> new JobAnalysisPostingResponse(
                        posting.getId(),
                        posting.getJobPostingId(),
                        posting.getProviderPostingId(),
                        posting.getProvider(),
                        posting.getCompanyName(),
                        posting.getOriginalJobTitle(),
                        posting.getSourceUrl(),
                        parseComparison(posting.getComparisonJson())
                ))
                .toList();
    }

    /**
     * 기능: 비교 결과의 근거 식별자를 화면에 표시할 실제 최소 문장과 연결한다.
     * 반환 값: 공고 담당 업무와 사용자가 확정한 프로젝트 담당 업무 근거를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisEvidenceResponse> listEvidence(
            JobAnalysis jobAnalysis
    ) {
        List<JobAnalysisPosting> postings = listPostings(jobAnalysis.getId());
        if (postings.isEmpty()) {
            return List.of();
        }

        List<JobAnalysisEvidenceResponse> evidenceResponses = new ArrayList<>();
        for (JobAnalysisPosting posting : postings) {
            parseJobEvidence(posting).forEach(evidence ->
                    evidenceResponses.add(new JobAnalysisEvidenceResponse(
                            evidence.evidenceId(),
                            "JOB_POSTING",
                            posting.getJobPostingId(),
                            evidence.text()
                    )));
        }

        for (ConfirmedProjectResponsibilityEvidence responsibility
                : jobAnalysisExecutionService
                        .listConfirmedResponsibilities(jobAnalysis)) {
            evidenceResponses.add(new JobAnalysisEvidenceResponse(
                    responsibility.evidenceId().toString(),
                    "USER_PROJECT",
                    responsibility.projectSourceId(),
                    responsibility.text()
            ));
        }
        return List.copyOf(evidenceResponses);
    }

    private List<JobAnalysisPosting> listPostings(UUID jobAnalysisId) {
        return jobAnalysisPostingRepository
                .findByJobAnalysisIdOrderByCreatedAtAsc(jobAnalysisId);
    }

    private List<PythonEvidenceSimilarityRequest.JobEvidence> parseJobEvidence(
            JobAnalysisPosting posting
    ) {
        try {
            return jobAnalysisJsonCodec.parseJobEvidence(
                    posting.getExtractionJson()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "STORED_EXTRACTION_JSON_INVALID",
                    exception
            );
        }
    }

    private JobPostingComparisonSnapshot parseComparison(
            String comparisonJson
    ) {
        if (comparisonJson == null) {
            return null;
        }
        try {
            return jobAnalysisJsonCodec.parseComparison(comparisonJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "STORED_COMPARISON_JSON_INVALID",
                    exception
            );
        }
    }
}
