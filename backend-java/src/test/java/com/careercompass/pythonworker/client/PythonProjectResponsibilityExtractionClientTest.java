package com.careercompass.pythonworker.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.careercompass.pythonworker.config.ProjectResponsibilityExtractionPolicyProperties;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionEnvelope;
import com.careercompass.pythonworker.dto.PythonProjectResponsibilityExtractionRequest;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionException;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionFailure;
import com.careercompass.pythonworker.exception.PythonProjectResponsibilityExtractionResponseViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonProjectResponsibilityExtractionClientTest {

    private static final String BASE_URL = "http://python-worker.test";
    private static final String TASK_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROJECT_SOURCE_ID = "22222222-2222-2222-2222-222222222222";
    private static final String REPOSITORY_VERSION = "abc123";

    private PythonProjectResponsibilityExtractionClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PythonProjectResponsibilityExtractionClient(
                builder.build(),
                new PythonWorkerProperties(
                        BASE_URL, "test-token",
                        Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new ProjectResponsibilityExtractionPolicyProperties(
                        10, 30, 3, 20, 10, 30, 2000, 20000, 500));
    }

    @Test
    void extract_validResponse_returnsValidatedData() {
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withSuccess(successEnvelope(""), MediaType.APPLICATION_JSON));

        PythonProjectResponsibilityExtractionEnvelope.Data data = client.extract(validRequest());

        assertThat(data.detectedTechnologies()).singleElement()
                .extracting(PythonProjectResponsibilityExtractionEnvelope
                        .DetectedTechnology::detectedName)
                .isEqualTo("react");
        assertThat(data.responsibilityEvidenceCandidates()).singleElement()
                .extracting(PythonProjectResponsibilityExtractionEnvelope
                        .ResponsibilityEvidenceCandidate::confirmationStatus)
                .isEqualTo("UNCONFIRMED");
        server.verify();
    }

    @Test
    void extract_emptyResultArrays_returnsValidatedData() {
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withSuccess(emptyResultsEnvelope(), MediaType.APPLICATION_JSON));

        PythonProjectResponsibilityExtractionEnvelope.Data data = client.extract(validRequest());

        assertThat(data.detectedTechnologies()).isEmpty();
        assertThat(data.responsibilityEvidenceCandidates()).isEmpty();
        server.verify();
    }

    @Test
    void extract_malformedJson_throwsNonRetryableResponseInvalid() {
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extract(validRequest()))
                .isInstanceOf(PythonProjectResponsibilityExtractionException.class)
                .satisfies(exception -> {
                    PythonProjectResponsibilityExtractionException extractionException =
                            (PythonProjectResponsibilityExtractionException) exception;
                    assertThat(extractionException.getFailure())
                            .isEqualTo(
                                    PythonProjectResponsibilityExtractionFailure.RESPONSE_INVALID);
                    assertThat(extractionException.getResponseViolation())
                            .isEqualTo(PythonProjectResponsibilityExtractionResponseViolation
                                    .RESPONSE_DESERIALIZATION_INVALID);
                    assertThat(extractionException.isRetryable()).isFalse();
                });
    }

    @Test
    void extract_unknownEvidenceReference_throwsResponseInvalid() {
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withSuccess(
                        successEnvelope("\"evidenceIds\":[\"unknown\"],"),
                        MediaType.APPLICATION_JSON));

        assertViolation(
                PythonProjectResponsibilityExtractionResponseViolation.EVIDENCE_REFERENCE_INVALID);
    }

    @Test
    void extract_pythonTechnologyTagField_throwsResponseInvalid() {
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withSuccess(
                        successEnvelope(
                                "\"technologyTagId\":\"70000000-0000-0000-0000-000000000001\","),
                        MediaType.APPLICATION_JSON));

        assertViolation(
                PythonProjectResponsibilityExtractionResponseViolation
                        .FORBIDDEN_TECHNOLOGY_FIELD);
    }

    @Test
    void extract_duplicateSelectedTag_rejectsBeforeHttpCall() {
        PythonProjectResponsibilityExtractionRequest valid = validRequest();
        PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag tag =
                valid.selectedTechnologyTags().getFirst();
        PythonProjectResponsibilityExtractionRequest duplicate =
                new PythonProjectResponsibilityExtractionRequest(
                        TASK_ID, PROJECT_SOURCE_ID, List.of(tag, tag),
                        valid.repositorySnapshot());

        assertThatThrownBy(() -> client.extract(duplicate))
                .isInstanceOf(PythonProjectResponsibilityExtractionException.class)
                .extracting(exception -> ((PythonProjectResponsibilityExtractionException)
                        exception).getFailure())
                .isEqualTo(PythonProjectResponsibilityExtractionFailure.REQUEST_INVALID);
        server.verify();
    }

    @Test
    void extract_modelUnavailable_mapsRetryableFailure() {
        String body = """
                {
                  "requestId":"request-1",
                  "data":null,
                  "error":{
                    "errorType":"PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE",
                    "retryable":true
                  },
                  "timestamp":"2026-08-13T10:00:00Z"
                }
                """;
        server.expect(requestTo(
                        BASE_URL + "/internal/v1/project-responsibility-extractions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON).body(body));

        assertThatThrownBy(() -> client.extract(validRequest()))
                .isInstanceOf(PythonProjectResponsibilityExtractionException.class)
                .satisfies(exception -> {
                    PythonProjectResponsibilityExtractionException extractionException =
                            (PythonProjectResponsibilityExtractionException) exception;
                    assertThat(extractionException.getFailure())
                            .isEqualTo(
                                    PythonProjectResponsibilityExtractionFailure.MODEL_UNAVAILABLE);
                    assertThat(extractionException.isRetryable()).isTrue();
                });
    }

    private void assertViolation(
            PythonProjectResponsibilityExtractionResponseViolation expectedViolation
    ) {
        assertThatThrownBy(() -> client.extract(validRequest()))
                .isInstanceOf(PythonProjectResponsibilityExtractionException.class)
                .satisfies(exception -> {
                    PythonProjectResponsibilityExtractionException extractionException =
                            (PythonProjectResponsibilityExtractionException) exception;
                    assertThat(extractionException.getFailure())
                            .isEqualTo(
                                    PythonProjectResponsibilityExtractionFailure.RESPONSE_INVALID);
                    assertThat(extractionException.getResponseViolation())
                            .isEqualTo(expectedViolation);
                });
    }

    private PythonProjectResponsibilityExtractionRequest validRequest() {
        return new PythonProjectResponsibilityExtractionRequest(
                TASK_ID,
                PROJECT_SOURCE_ID,
                List.of(new PythonProjectResponsibilityExtractionRequest.SelectedTechnologyTag(
                        "70000000-0000-0000-0000-000000000001", "React")),
                new PythonProjectResponsibilityExtractionRequest.RepositorySnapshot(
                        "https://github.com/example/sample",
                        Instant.parse("2026-08-13T09:00:00Z"),
                        REPOSITORY_VERSION,
                        "Sample project",
                        List.of(new PythonProjectResponsibilityExtractionRequest.ReadmeEvidence(
                                "readme-1", "README.md", "React API를 구현했습니다.")),
                        List.of(new PythonProjectResponsibilityExtractionRequest.FileEvidence(
                                "file-1", "package.json", "MANIFEST",
                                "{\"dependencies\":{\"react\":\"18.0.0\"}}"))));
    }

    private String successEnvelope(String detectedTechnologyOverride) {
        String evidenceIds = detectedTechnologyOverride.contains("\"evidenceIds\"")
                ? "" : "\"evidenceIds\":[\"file-1\"],";
        return """
                {
                  "requestId":"request-1",
                  "data":{
                    "extractionTaskId":"%s",
                    "projectSourceId":"%s",
                    "repositoryVersion":"%s",
                    "detectedTechnologies":[{
                      "detectedName":"react",
                      "source":"MANIFEST",
                      %s
                      %s
                      "contractMarker":null
                    }],
                    "responsibilityEvidenceCandidates":[{
                      "evidenceId":"project-responsibility-1",
                      "category":"PROJECT_RESPONSIBILITY",
                      "text":"React API 구현",
                      "sourceEvidenceIds":["readme-1","file-1"],
                      "confirmationStatus":"UNCONFIRMED"
                    }],
                    "modelExecution":{
                      "stage":"PROJECT_RESPONSIBILITY_EXTRACTION",
                      "provider":"OLLAMA",
                      "model":"qwen2.5:latest"
                    }
                  },
                  "error":null,
                  "timestamp":"2026-08-13T10:00:00Z"
                }
                """.formatted(
                TASK_ID, PROJECT_SOURCE_ID, REPOSITORY_VERSION,
                evidenceIds, detectedTechnologyOverride);
    }

    private String emptyResultsEnvelope() {
        return """
                {
                  "requestId":"request-1",
                  "data":{
                    "extractionTaskId":"%s",
                    "projectSourceId":"%s",
                    "repositoryVersion":"%s",
                    "detectedTechnologies":[],
                    "responsibilityEvidenceCandidates":[],
                    "modelExecution":{
                      "stage":"PROJECT_RESPONSIBILITY_EXTRACTION",
                      "provider":"OLLAMA",
                      "model":"qwen2.5:latest"
                    }
                  },
                  "error":null,
                  "timestamp":"2026-08-13T10:00:00Z"
                }
                """.formatted(TASK_ID, PROJECT_SOURCE_ID, REPOSITORY_VERSION);
    }
}
