package com.careercompass.pythonworker.client;

import com.careercompass.document.domain.DocumentType;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.DocumentExtractionSuccessResponse;
import com.careercompass.pythonworker.exception.PythonDocumentExtractionContractException;
import com.careercompass.pythonworker.exception.PythonDocumentExtractionException;
import com.careercompass.pythonworker.exception.PythonPiiLlmPipelineNotImplementedException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(PythonDocumentExtractionClient.class)
@EnableConfigurationProperties(PythonWorkerProperties.class)
@TestPropertySource(properties = {
        "python.worker.base-url=http://python-worker.test",
        "python.worker.internal-token=test-internal-service-token"
})
class PythonDocumentExtractionClientTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("7b94df20-7e9f-4df7-bc90-408306e1fcd6");
    private static final UUID EXTRACTION_TASK_ID =
            UUID.fromString("25a89eb8-224f-4457-ae6f-53dc32414f0d");

    @Autowired
    private PythonDocumentExtractionClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void extract_returnsValidatedEnvelope_whenPythonRespondsWithContractSuccess() {
        server.expect(requestTo("http://python-worker.test/internal/v1/documents/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-internal-service-token"))
                .andExpect(header("X-Request-Id", containsString("-")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("name=\"documentId\"")))
                .andExpect(content().string(containsString(DOCUMENT_ID.toString())))
                .andExpect(content().string(containsString("name=\"extractionTaskId\"")))
                .andExpect(content().string(containsString(EXTRACTION_TASK_ID.toString())))
                .andExpect(content().string(containsString("name=\"documentType\"")))
                .andExpect(content().string(containsString("RESUME")))
                .andExpect(content().string(containsString("filename=\"resume.pdf\"")))
                .andExpect(content().string(containsString("Content-Type: application/pdf")))
                .andRespond(request -> {
                    String requestId = request.getHeaders().getFirst("X-Request-Id");
                    return withSuccess(successJson(requestId, true), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        DocumentExtractionSuccessResponse response = client.extract(
                DOCUMENT_ID,
                EXTRACTION_TASK_ID,
                DocumentType.RESUME,
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                "resume.pdf"
        );

        assertThat(response.data().status().name()).isEqualTo("EXTRACTED");
        assertThat(response.data().piiRemoved()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("contractErrors")
    void extract_throwsParsedFailure_whenPythonRespondsWithContractError(
            HttpStatus status,
            String errorType,
            boolean retryable
    ) {
        expectError(status, errorType, retryable);

        assertThatThrownBy(this::extractResume)
                .isInstanceOfSatisfying(PythonDocumentExtractionException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(status.value());
                    assertThat(exception.getResponse().error().errorType()).isEqualTo(errorType);
                    assertThat(exception.getResponse().error().retryable()).isEqualTo(retryable);
                });
    }

    @Test
    void extract_parsesFieldErrorDetails_whenPythonRejectsRequestFields() {
        server.expect(requestTo("http://python-worker.test/internal/v1/documents/extract"))
                .andRespond(request -> {
                    String requestId = request.getHeaders().getFirst("X-Request-Id");
                    String responseBody = """
                            {
                              "requestId": "%s",
                              "data": null,
                              "error": {
                                "errorType": "INVALID_EXTRACTION_REQUEST",
                                "message": "요청 필드가 계약을 따르지 않습니다.",
                                "fieldErrors": [
                                  {"fieldName": "documentId", "message": "UUID 형식이어야 합니다."}
                                ],
                                "retryable": false
                              },
                              "timestamp": "2026-07-28T08:00:00Z"
                            }
                            """.formatted(requestId);
                    return withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(responseBody)
                            .createResponse(request);
                });

        assertThatThrownBy(this::extractResume)
                .isInstanceOfSatisfying(PythonDocumentExtractionException.class, exception ->
                        assertThat(exception.getResponse().error().fieldErrors().getFirst().fieldName())
                                .isEqualTo("documentId"));
    }

    @Test
    void extract_throwsPipelineNotImplemented_whenPythonStopsBeforePiiAndLlmPipeline() {
        expectError(HttpStatus.NOT_IMPLEMENTED, "PII_LLM_PIPELINE_NOT_IMPLEMENTED", false);

        assertThatThrownBy(this::extractResume)
                .isInstanceOf(PythonPiiLlmPipelineNotImplementedException.class);
    }

    @Test
    void extract_rejectsSuccess_whenPiiRemovalIsNotConfirmed() {
        server.expect(requestTo("http://python-worker.test/internal/v1/documents/extract"))
                .andRespond(request -> {
                    String requestId = request.getHeaders().getFirst("X-Request-Id");
                    return withSuccess(successJson(requestId, false), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        assertThatThrownBy(this::extractResume)
                .isInstanceOf(PythonDocumentExtractionContractException.class);
    }

    @Test
    void extract_rejectsSuccess_whenResponseContainsUnknownField() {
        server.expect(requestTo("http://python-worker.test/internal/v1/documents/extract"))
                .andRespond(request -> {
                    String requestId = request.getHeaders().getFirst("X-Request-Id");
                    String responseBody = successJson(requestId, true)
                            .replace(
                                    "\"error\": null,",
                                    "\"error\": null, \"unexpected\": true,"
                            );
                    return withSuccess(responseBody, MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        assertThatThrownBy(this::extractResume).hasMessageContaining("unexpected");
    }

    @Test
    void extract_rejectsError_whenHttpStatusDoesNotMatchErrorType() {
        expectError(HttpStatus.BAD_GATEWAY, "MODEL_UNAVAILABLE", true);

        assertThatThrownBy(this::extractResume)
                .isInstanceOf(PythonDocumentExtractionContractException.class);
    }

    @Test
    void extract_rejectsEmptyPdfBeforeCallingPython() {
        assertThatThrownBy(() -> client.extract(
                DOCUMENT_ID,
                EXTRACTION_TASK_ID,
                DocumentType.RESUME,
                new byte[0],
                "resume.pdf"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void extractResume() {
        client.extract(
                DOCUMENT_ID,
                EXTRACTION_TASK_ID,
                DocumentType.RESUME,
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                "resume.pdf"
        );
    }

    private void expectError(HttpStatus status, String errorType, boolean retryable) {
        server.expect(requestTo("http://python-worker.test/internal/v1/documents/extract"))
                .andRespond(request -> {
                    String requestId = request.getHeaders().getFirst("X-Request-Id");
                    return withStatus(status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(requestId, errorType, retryable))
                            .createResponse(request);
                });
    }

    private String successJson(String requestId, boolean piiRemoved) {
        return """
                {
                  "requestId": "%s",
                  "data": {
                    "documentId": "%s",
                    "extractionTaskId": "%s",
                    "status": "EXTRACTED",
                    "candidate": {
                      "skills": [],
                      "workExperiences": [],
                      "projects": [],
                      "education": [],
                      "certifications": [],
                      "evidence": []
                    },
                    "modelProvider": "ollama",
                    "modelName": "configured-model-name",
                    "piiRemoved": %s
                  },
                  "error": null,
                  "timestamp": "2026-07-28T08:00:00Z"
                }
                """.formatted(requestId, DOCUMENT_ID, EXTRACTION_TASK_ID, piiRemoved);
    }

    private String errorJson(String requestId, String errorType, boolean retryable) {
        return """
                {
                  "requestId": "%s",
                  "data": null,
                  "error": {
                    "errorType": "%s",
                    "message": "Python 문서 추출 실패",
                    "fieldErrors": [],
                    "retryable": %s
                  },
                  "timestamp": "2026-07-28T08:00:00Z"
                }
                """.formatted(requestId, errorType, retryable);
    }

    private static Stream<Arguments> contractErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, "INTERNAL_TOKEN_REQUIRED", false),
                Arguments.of(HttpStatus.UNAUTHORIZED, "INTERNAL_UNAUTHORIZED", false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_EXTRACTION_REQUEST", false),
                Arguments.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", false),
                Arguments.of(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, "PDF_UNREADABLE", false),
                Arguments.of(HttpStatus.UNPROCESSABLE_CONTENT, "NO_EXTRACTABLE_TEXT", false),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "PII_SANITIZATION_FAILED", false),
                Arguments.of(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", true),
                Arguments.of(HttpStatus.BAD_GATEWAY, "MODEL_RESPONSE_INVALID", false),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, "EXTRACTION_FAILED", false)
        );
    }
}
