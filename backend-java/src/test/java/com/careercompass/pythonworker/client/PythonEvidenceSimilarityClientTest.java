package com.careercompass.pythonworker.client;

import java.time.Duration;
import java.util.List;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityEnvelope;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityException;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityFailure;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityResponseViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonEvidenceSimilarityClientTest {

    private static final String BASE_URL = "http://python-worker.test";
    private static final String COMPARISON_TASK_ID = "11111111-1111-1111-1111-111111111111";
    private static final String JOB_ANALYSIS_ID = "22222222-2222-2222-2222-222222222222";
    private static final String JOB_POSTING_ID = "33333333-3333-3333-3333-333333333333";

    private PythonEvidenceSimilarityClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PythonEvidenceSimilarityClient(
                builder.build(),
                new PythonWorkerProperties(
                        BASE_URL, "test-token",
                        Duration.ofSeconds(3), Duration.ofSeconds(10)));
    }

    @Test
    void compare_validCalculatedResponse_returnsValidatedData() {
        server.expect(requestTo(BASE_URL + "/internal/v1/job-evidence-similarities"))
                .andRespond(withSuccess(calculatedEnvelope("user-1"), MediaType.APPLICATION_JSON));

        PythonEvidenceSimilarityEnvelope.Data data = client.compare(requestWithUserEvidence());

        assertThat(data.status()).isEqualTo("CALCULATED");
        assertThat(data.results()).singleElement()
                .extracting(PythonEvidenceSimilarityEnvelope.Result::judgment)
                .isEqualTo("RELATED");
        server.verify();
    }

    @Test
    void compare_emptyUserEvidenceAndNotCalculableResponse_returnsNormalResult() {
        server.expect(requestTo(BASE_URL + "/internal/v1/job-evidence-similarities"))
                .andRespond(withSuccess(notCalculableEnvelope(), MediaType.APPLICATION_JSON));

        PythonEvidenceSimilarityRequest request = new PythonEvidenceSimilarityRequest(
                COMPARISON_TASK_ID, JOB_ANALYSIS_ID, JOB_POSTING_ID,
                requestWithUserEvidence().jobEvidence(), List.of());
        PythonEvidenceSimilarityEnvelope.Data data = client.compare(request);

        assertThat(data.status()).isEqualTo("NOT_CALCULABLE");
        assertThat(data.results()).singleElement()
                .extracting(PythonEvidenceSimilarityEnvelope.Result::unavailableReason)
                .isEqualTo("COMPATIBLE_USER_EVIDENCE_MISSING");
    }

    @Test
    void compare_resultReferencingUnknownUserEvidence_throwsResponseInvalid() {
        server.expect(requestTo(BASE_URL + "/internal/v1/job-evidence-similarities"))
                .andRespond(withSuccess(calculatedEnvelope("unknown-user"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.compare(requestWithUserEvidence()))
                .isInstanceOf(PythonEvidenceSimilarityException.class)
                .satisfies(exception -> {
                    PythonEvidenceSimilarityException similarityException =
                            (PythonEvidenceSimilarityException) exception;
                    assertThat(similarityException.getFailure())
                            .isEqualTo(PythonEvidenceSimilarityFailure.RESPONSE_INVALID);
                    assertThat(similarityException.getResponseViolation())
                            .isEqualTo(PythonEvidenceSimilarityResponseViolation.RESULT_COMBINATION_INVALID);
                });
    }

    @Test
    void compare_modelUnavailable_mapsRetryableFailure() {
        String body = """
                {
                  "requestId":"request-1",
                  "data":null,
                  "error":{
                    "errorType":"SEMANTIC_COMPARISON_MODEL_UNAVAILABLE",
                    "message":"unavailable",
                    "fieldErrors":[],
                    "retryable":true
                  },
                  "timestamp":"2026-08-12T10:00:00Z"
                }
                """;
        server.expect(requestTo(BASE_URL + "/internal/v1/job-evidence-similarities"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON).body(body));

        assertThatThrownBy(() -> client.compare(requestWithUserEvidence()))
                .isInstanceOf(PythonEvidenceSimilarityException.class)
                .satisfies(exception -> {
                    PythonEvidenceSimilarityException similarityException =
                            (PythonEvidenceSimilarityException) exception;
                    assertThat(similarityException.getFailure())
                            .isEqualTo(PythonEvidenceSimilarityFailure.MODEL_UNAVAILABLE);
                    assertThat(similarityException.isRetryable()).isTrue();
                });
    }

    @Test
    void compare_duplicateEvidenceId_rejectsBeforeHttpCall() {
        PythonEvidenceSimilarityRequest request = new PythonEvidenceSimilarityRequest(
                COMPARISON_TASK_ID, JOB_ANALYSIS_ID, JOB_POSTING_ID,
                requestWithUserEvidence().jobEvidence(),
                List.of(new PythonEvidenceSimilarityRequest.UserEvidence(
                        "job-1", "project-1", "PROJECT_RESPONSIBILITY", "사용자 근거")));

        assertThatThrownBy(() -> client.compare(request))
                .isInstanceOf(PythonEvidenceSimilarityException.class)
                .extracting(exception -> ((PythonEvidenceSimilarityException) exception).getFailure())
                .isEqualTo(PythonEvidenceSimilarityFailure.REQUEST_INVALID);
        server.verify();
    }

    private PythonEvidenceSimilarityRequest requestWithUserEvidence() {
        return new PythonEvidenceSimilarityRequest(
                COMPARISON_TASK_ID,
                JOB_ANALYSIS_ID,
                JOB_POSTING_ID,
                List.of(new PythonEvidenceSimilarityRequest.JobEvidence(
                        "job-1", "RESPONSIBILITY", "백엔드 API 설계와 운영")),
                List.of(new PythonEvidenceSimilarityRequest.UserEvidence(
                        "user-1", "project-1", "PROJECT_RESPONSIBILITY",
                        "Spring Boot 주문 API 설계와 운영")));
    }

    private String calculatedEnvelope(String bestMatchUserEvidenceId) {
        return successEnvelope("""
                "status":"CALCULATED",
                "method":"LLM_JUDGE",
                "results":[{
                  "jobEvidenceId":"job-1",
                  "status":"CALCULATED",
                  "bestMatchUserEvidenceId":"%s",
                  "score":null,
                  "judgment":"RELATED",
                  "unavailableReason":null
                }]
                """.formatted(bestMatchUserEvidenceId));
    }

    private String notCalculableEnvelope() {
        return successEnvelope("""
                "status":"NOT_CALCULABLE",
                "method":"LLM_JUDGE",
                "results":[{
                  "jobEvidenceId":"job-1",
                  "status":"NOT_CALCULABLE",
                  "bestMatchUserEvidenceId":null,
                  "score":null,
                  "judgment":null,
                  "unavailableReason":"COMPATIBLE_USER_EVIDENCE_MISSING"
                }]
                """);
    }

    private String successEnvelope(String resultFields) {
        return """
                {
                  "requestId":"request-1",
                  "data":{
                    "comparisonTaskId":"%s",
                    "jobAnalysisId":"%s",
                    "jobPostingId":"%s",
                    %s,
                    "modelExecution":{
                      "stage":"EVIDENCE_SEMANTIC_COMPARISON",
                      "provider":"OLLAMA",
                      "model":"qwen2.5:latest"
                    }
                  },
                  "error":null,
                  "timestamp":"2026-08-12T10:00:00Z"
                }
                """.formatted(
                COMPARISON_TASK_ID, JOB_ANALYSIS_ID, JOB_POSTING_ID, resultFields);
    }
}
