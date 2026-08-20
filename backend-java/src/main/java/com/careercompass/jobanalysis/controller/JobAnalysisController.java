package com.careercompass.jobanalysis.controller;

import java.net.URI;
import java.util.UUID;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.dto.CreateJobAnalysisRequest;
import com.careercompass.jobanalysis.dto.JobAnalysisResponse;
import com.careercompass.jobanalysis.service.JobAnalysisResultService;
import com.careercompass.jobanalysis.service.JobAnalysisService;
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
    private final JobAnalysisResultService jobAnalysisResultService;
    private final ApiResponseFactory responseFactory;

    public JobAnalysisController(
            JobAnalysisService jobAnalysisService,
            JobAnalysisResultService jobAnalysisResultService,
            ApiResponseFactory responseFactory
    ) {
        this.jobAnalysisService = jobAnalysisService;
        this.jobAnalysisResultService = jobAnalysisResultService;
        this.responseFactory = responseFactory;
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

    @GetMapping("/{jobAnalysisId}")
    public ResponseEntity<ApiResponse<JobAnalysisResponse>> getJobAnalysis(
            @PathVariable UUID jobAnalysisId
    ) {
        JobAnalysis jobAnalysis =
                jobAnalysisService.getCurrentUserJobAnalysis(jobAnalysisId);
        return ResponseEntity.ok(responseFactory.success(toResponse(jobAnalysis)));
    }

    private JobAnalysisResponse toResponse(JobAnalysis jobAnalysis) {
        return new JobAnalysisResponse(
                jobAnalysis.getId(),
                jobAnalysis.getAnalysisStatus().name(),
                jobAnalysis.getCurrentStep().name(),
                jobAnalysis.getCompletedUnits(),
                jobAnalysis.getTotalUnits(),
                jobAnalysis.getFailureCode() != null
                        ? jobAnalysis.getFailureCode().name()
                        : null,
                jobAnalysisResultService.listPostingResults(jobAnalysis.getId())
        );
    }
}
