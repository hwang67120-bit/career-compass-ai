package com.careercompass.jobanalysis.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.dto.CreateJobAnalysisRequest;
import com.careercompass.jobanalysis.dto.JobAnalysisPostingResponse;
import com.careercompass.jobanalysis.dto.JobAnalysisResponse;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-analyses")
public class JobAnalysisController {

    private final JobAnalysisService jobAnalysisService;
    private final ApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public JobAnalysisController(
            JobAnalysisService jobAnalysisService,
            ApiResponseFactory responseFactory,
            ObjectMapper objectMapper
    ) {
        this.jobAnalysisService = jobAnalysisService;
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createJobAnalysis(
            @RequestBody(required = false) CreateJobAnalysisRequest request
    ) {
        UUID jobAnalysisId =
                jobAnalysisService.createCurrentUserJobAnalysis(request);
        return ResponseEntity.accepted()
                .location(URI.create(
                        "/api/v1/job-analyses/" + jobAnalysisId
                ))
                .body(responseFactory.success(null));
    }

    /**
     * 2026-08-04 임시 작업(코덱스 사용량 한도 공백기 대응) — 지금은 상태·단계와 저장된
     * 채용공고 추출 결과만 반환한다. SSE·재연결 이벤트는 아직 없다(확인 필요, 계획 파일 참고).
     */
    @GetMapping("/{jobAnalysisId}")
    public ResponseEntity<ApiResponse<JobAnalysisResponse>> getJobAnalysis(
            @PathVariable UUID jobAnalysisId
    ) {
        JobAnalysis jobAnalysis =
                jobAnalysisService.getCurrentUserJobAnalysis(jobAnalysisId);
        List<JobAnalysisPosting> postings =
                jobAnalysisService.listPostings(jobAnalysisId);
        return ResponseEntity.ok(
                responseFactory.success(toResponse(jobAnalysis, postings)));
    }

    private JobAnalysisResponse toResponse(
            JobAnalysis jobAnalysis,
            List<JobAnalysisPosting> postings
    ) {
        return new JobAnalysisResponse(
                jobAnalysis.getId(),
                jobAnalysis.getAnalysisStatus().name(),
                jobAnalysis.getCurrentStep().name(),
                postings.stream().map(this::toPostingResponse).toList()
        );
    }

    private JobAnalysisPostingResponse toPostingResponse(JobAnalysisPosting posting) {
        try {
            return new JobAnalysisPostingResponse(
                    posting.getProviderPostingId(),
                    posting.getCompanyName(),
                    posting.getOriginalJobTitle(),
                    posting.getSourceUrl(),
                    objectMapper.readTree(posting.getExtractionJson()),
                    objectMapper.readTree(posting.getModelExecutionsJson())
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "저장된 추출 결과 JSON을 읽을 수 없습니다.", exception);
        }
    }
}
