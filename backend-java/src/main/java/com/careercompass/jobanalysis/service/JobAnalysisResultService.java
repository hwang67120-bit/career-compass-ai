package com.careercompass.jobanalysis.service;

import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.dto.JobAnalysisPostingResponse;
import com.careercompass.jobanalysis.dto.JobPostingComparisonSnapshot;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class JobAnalysisResultService {

    private final JobAnalysisPostingRepository jobAnalysisPostingRepository;
    private final JobAnalysisJsonCodec jobAnalysisJsonCodec;

    /**
     * 기능: 분석 작업에 저장된 공고 메타데이터와 의미 비교 결과를 사용자 응답 형태로 조회한다.
     * 반환 값: 저장 순서대로 정렬된 공고별 비교 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<JobAnalysisPostingResponse> listPostingResults(
            UUID jobAnalysisId
    ) {
        return jobAnalysisPostingRepository
                .findByJobAnalysisIdOrderByCreatedAtAsc(jobAnalysisId)
                .stream()
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
