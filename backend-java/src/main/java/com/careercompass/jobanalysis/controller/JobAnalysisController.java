package com.careercompass.jobanalysis.controller;

import java.net.URI;
import java.util.UUID;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.jobanalysis.dto.CreateJobAnalysisRequest;
import com.careercompass.jobanalysis.service.JobAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-analyses")
public class JobAnalysisController {

    private final JobAnalysisService jobAnalysisService;
    private final ApiResponseFactory responseFactory;

    public JobAnalysisController(
            JobAnalysisService jobAnalysisService,
            ApiResponseFactory responseFactory
    ) {
        this.jobAnalysisService = jobAnalysisService;
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
}
