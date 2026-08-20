package com.careercompass.jobanalysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.careercompass.jobanalysis.domain.JobAnalysis;
import com.careercompass.jobanalysis.domain.JobAnalysisFailureCode;
import com.careercompass.jobanalysis.domain.JobAnalysisPosting;
import com.careercompass.pythonworker.client.PythonEvidenceSimilarityClient;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityEnvelope;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityException;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobEvidenceComparisonServiceTest {

    private static final UUID ANALYSIS_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROJECT_SOURCE_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_EVIDENCE_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private JobAnalysisExecutionService jobAnalysisExecutionService;
    private PythonEvidenceSimilarityClient client;
    private JobEvidenceComparisonService service;
    private JobAnalysis analysis;

    @BeforeEach
    void setUp() {
        jobAnalysisExecutionService = mock(JobAnalysisExecutionService.class);
        client = mock(PythonEvidenceSimilarityClient.class);
        service = new JobEvidenceComparisonService(
                jobAnalysisExecutionService,
                client,
                new JobAnalysisJsonCodec(new ObjectMapper())
        );
        analysis = mock(JobAnalysis.class);
        when(analysis.getId()).thenReturn(ANALYSIS_ID);
        when(jobAnalysisExecutionService.listConfirmedResponsibilities(analysis))
                .thenReturn(List.of(new ConfirmedProjectResponsibility(
                        USER_EVIDENCE_ID,
                        PROJECT_SOURCE_ID,
                        "Redis 캐시를 적용해 API 부하를 줄였습니다."
                )));
    }

    @Test
    void compare_withLinkedEvidence_callsJudgeAndStoresCalculatedResult() {
        JobAnalysisPosting posting = posting("posting-1", extractionWithResponsibility());
        when(jobAnalysisExecutionService.listPostings(ANALYSIS_ID)).thenReturn(List.of(posting));
        when(client.compare(any())).thenAnswer(invocation -> {
            PythonEvidenceSimilarityRequest request = invocation.getArgument(0);
            return calculatedResponse(request);
        });

        service.compare(analysis);

        ArgumentCaptor<PythonEvidenceSimilarityRequest> requestCaptor =
                ArgumentCaptor.forClass(PythonEvidenceSimilarityRequest.class);
        verify(client).compare(requestCaptor.capture());
        assertThat(requestCaptor.getValue().jobEvidence())
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.evidenceId()).isEqualTo("r1");
                    assertThat(evidence.category()).isEqualTo("RESPONSIBILITY");
                    assertThat(evidence.text()).isEqualTo("대규모 API를 개발합니다.");
                });
        assertThat(requestCaptor.getValue().userEvidence())
                .singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.evidenceId())
                            .isEqualTo(USER_EVIDENCE_ID.toString());
                    assertThat(evidence.projectSourceId())
                            .isEqualTo(PROJECT_SOURCE_ID.toString());
                });

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobAnalysisExecutionService).recordPostingComparison(
                eq(ANALYSIS_ID), eq(posting.getId()), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("\"status\":\"CALCULATED\"")
                .contains("\"judgment\":\"RELATED\"");
        verify(jobAnalysisExecutionService).finishEvidenceComparison(
                ANALYSIS_ID, 1, 1, 1, null);
    }

    @Test
    void compare_withoutJobEvidence_skipsJudgeAndStoresNotCalculable() {
        JobAnalysisPosting posting = posting(
                "posting-1",
                "{\"responsibilities\":[],\"evidence\":[]}"
        );
        when(jobAnalysisExecutionService.listPostings(ANALYSIS_ID)).thenReturn(List.of(posting));

        service.compare(analysis);

        verify(client, never()).compare(any());
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobAnalysisExecutionService).recordPostingComparison(
                eq(ANALYSIS_ID), eq(posting.getId()), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("\"status\":\"NOT_CALCULABLE\"")
                .contains("JOB_EVIDENCE_EMPTY_AFTER_SANITIZATION")
                .contains("\"method\":null");
        verify(jobAnalysisExecutionService).finishEvidenceComparison(
                ANALYSIS_ID, 1, 1, 0, null);
    }

    @Test
    void compare_withOneSuccessfulCallAndOneFailure_finishesPartiallyCompleted() {
        JobAnalysisPosting first = posting("posting-1", extractionWithResponsibility());
        JobAnalysisPosting second = posting("posting-2", extractionWithResponsibility());
        when(jobAnalysisExecutionService.listPostings(ANALYSIS_ID))
                .thenReturn(List.of(first, second));
        when(client.compare(any()))
                .thenAnswer(invocation -> calculatedResponse(invocation.getArgument(0)))
                .thenThrow(new PythonEvidenceSimilarityException(
                        PythonEvidenceSimilarityFailure.MODEL_UNAVAILABLE));

        service.compare(analysis);

        verify(jobAnalysisExecutionService).finishEvidenceComparison(
                ANALYSIS_ID,
                1,
                2,
                1,
                JobAnalysisFailureCode.EVIDENCE_COMPARISON_MODEL_UNAVAILABLE
        );
    }

    private PythonEvidenceSimilarityEnvelope.Data calculatedResponse(
            PythonEvidenceSimilarityRequest request
    ) {
        return new PythonEvidenceSimilarityEnvelope.Data(
                request.comparisonTaskId(),
                request.jobAnalysisId(),
                request.jobPostingId(),
                "CALCULATED",
                "LLM_JUDGE",
                List.of(new PythonEvidenceSimilarityEnvelope.Result(
                        "r1",
                        "CALCULATED",
                        USER_EVIDENCE_ID.toString(),
                        null,
                        "RELATED",
                        null
                )),
                new PythonEvidenceSimilarityEnvelope.ModelExecution(
                        "EVIDENCE_SEMANTIC_COMPARISON",
                        "OLLAMA",
                        "qwen2.5:latest"
                )
        );
    }

    private JobAnalysisPosting posting(String providerPostingId, String extractionJson) {
        return JobAnalysisPosting.create(
                UUID.randomUUID(),
                ANALYSIS_ID,
                providerPostingId,
                "DEV_SAMPLE",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "예시회사",
                "백엔드 개발자",
                "https://example.invalid/" + providerPostingId,
                extractionJson,
                "[]",
                Instant.parse("2026-08-19T00:00:00Z")
        );
    }

    private String extractionWithResponsibility() {
        return """
                {
                  "responsibilities":[
                    {"rawText":"API 개발","evidenceIds":["r1"]}
                  ],
                  "evidence":[
                    {
                      "evidenceId":"r1",
                      "fieldPath":"responsibilities[0]",
                      "value":"API 개발",
                      "sourceText":"대규모 API를 개발합니다."
                    }
                  ]
                }
                """;
    }
}
