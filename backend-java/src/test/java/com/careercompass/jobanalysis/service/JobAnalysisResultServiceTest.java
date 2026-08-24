package com.careercompass.jobanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.jobanalysis.dto.JobAnalysisEvidenceResponse;
import com.careercompass.jobanalysis.repository.JobAnalysisPostingRepository;
import com.careercompass.jobanalysis.service.model.ConfirmedProjectResponsibilityEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JobAnalysisResultServiceTest {

    private final JobAnalysisPostingRepository postingRepository =
            mock(JobAnalysisPostingRepository.class);
    private final JobAnalysisExecutionService executionService =
            mock(JobAnalysisExecutionService.class);
    private final JobAnalysisResultService resultService =
            new JobAnalysisResultService(
                    postingRepository,
                    executionService,
                    new JobAnalysisJsonCodec(new ObjectMapper())
            );

    @Test
    void listEvidence_withJobAndConfirmedProjectEvidence_returnsActualTexts() {
        UUID jobAnalysisId = UUID.randomUUID();
        UUID jobPostingId = UUID.randomUUID();
        UUID userEvidenceId = UUID.randomUUID();
        UUID projectSourceId = UUID.randomUUID();
        JobAnalysis jobAnalysis = mock(JobAnalysis.class);
        JobAnalysisPosting posting = mock(JobAnalysisPosting.class);

        when(jobAnalysis.getId()).thenReturn(jobAnalysisId);
        when(posting.getJobPostingId()).thenReturn(jobPostingId);
        when(posting.getExtractionJson()).thenReturn(
                "{\"evidence\":[{\"evidenceId\":\"job-evidence-1\","
                        + "\"sourceText\":\"REST API를 설계하고 개발합니다.\"}],"
                        + "\"responsibilities\":[{\"evidenceIds\":["
                        + "\"job-evidence-1\"]}]}"
        );
        when(postingRepository.findByJobAnalysisIdOrderByCreatedAtAsc(
                jobAnalysisId
        )).thenReturn(List.of(posting));
        when(executionService.listConfirmedResponsibilities(jobAnalysis))
                .thenReturn(List.of(
                        new ConfirmedProjectResponsibilityEvidence(
                                userEvidenceId,
                                projectSourceId,
                                "Spring Boot 분석 API를 구현했습니다."
                        )
                ));

        List<JobAnalysisEvidenceResponse> evidence =
                resultService.listEvidence(jobAnalysis);

        assertThat(evidence).containsExactly(
                new JobAnalysisEvidenceResponse(
                        "job-evidence-1",
                        "JOB_POSTING",
                        jobPostingId,
                        "REST API를 설계하고 개발합니다."
                ),
                new JobAnalysisEvidenceResponse(
                        userEvidenceId.toString(),
                        "USER_PROJECT",
                        projectSourceId,
                        "Spring Boot 분석 API를 구현했습니다."
                )
        );
    }
}
