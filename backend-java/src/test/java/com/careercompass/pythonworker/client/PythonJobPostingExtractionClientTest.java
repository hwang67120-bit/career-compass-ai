package com.careercompass.pythonworker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonExtractionFailure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;

/**
 * 계약: contracts/job-posting-extraction.md 4·5절. 저장 전 재검증(validateData)이
 * 실제로 요청과 다른 jobPostingId·extractionTaskId, EXTRACTED가 아닌 상태, 잘못된
 * modelExecutions를 걸러내는지 확인한다 — PR #48 리뷰에서 이 검증이 없다고 지적됨.
 */
@RestClientTest(PythonJobPostingExtractionClient.class)
@EnableConfigurationProperties(PythonWorkerProperties.class)
@TestPropertySource(properties = {
        "python.worker.base-url=http://python-worker.test",
        "python.worker.internal-token=test-internal-service-token"
})
class PythonJobPostingExtractionClientTest {

    private static final String JOB_POSTING_ID = "7b94df20-7e9f-4df7-bc90-408306e1fcd6";
    private static final String EXTRACTION_TASK_ID = "25a89eb8-224f-4457-ae6f-53dc32414f0d";
    private static final String VALID_MODEL_EXECUTIONS = """
            [
              {"stage":"CORE_EXTRACTION","provider":"OLLAMA","model":"core-model"},
              {"stage":"RESPONSIBILITY_EXTRACTION","provider":"GEMINI","model":"resp-model"}
            ]
            """;

    @Autowired
    private PythonJobPostingExtractionClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void extract_withValidResponse_returnsData() {
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, EXTRACTION_TASK_ID, "EXTRACTED", VALID_MODEL_EXECUTIONS),
                MediaType.APPLICATION_JSON
        ));

        PythonJobPostingExtractionEnvelope.Data data =
                client.extract(JOB_POSTING_ID, EXTRACTION_TASK_ID, "채용공고 본문");

        assertThat(data.jobPostingId()).isEqualTo(JOB_POSTING_ID);
        assertThat(data.extractionTaskId()).isEqualTo(EXTRACTION_TASK_ID);
        assertThat(data.status()).isEqualTo("EXTRACTED");
    }

    @Test
    void extract_withMismatchedJobPostingId_throwsResponseInvalid() {
        expectExtractCall().andRespond(withSuccess(
                envelope("different-job-posting-id", EXTRACTION_TASK_ID, "EXTRACTED", VALID_MODEL_EXECUTIONS),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    @Test
    void extract_withMismatchedExtractionTaskId_throwsResponseInvalid() {
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, "different-extraction-task-id", "EXTRACTED", VALID_MODEL_EXECUTIONS),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    @Test
    void extract_withNonExtractedStatus_throwsResponseInvalid() {
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, EXTRACTION_TASK_ID, "PENDING", VALID_MODEL_EXECUTIONS),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    @Test
    void extract_withMissingModelExecutionStage_throwsResponseInvalid() {
        String modelExecutions = """
                [{"stage":"CORE_EXTRACTION","provider":"OLLAMA","model":"core-model"}]
                """;
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, EXTRACTION_TASK_ID, "EXTRACTED", modelExecutions),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    @Test
    void extract_withUnknownModelExecutionProvider_throwsResponseInvalid() {
        String modelExecutions = """
                [
                  {"stage":"CORE_EXTRACTION","provider":"unknown-provider","model":"core-model"},
                  {"stage":"RESPONSIBILITY_EXTRACTION","provider":"OLLAMA","model":"resp-model"}
                ]
                """;
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, EXTRACTION_TASK_ID, "EXTRACTED", modelExecutions),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    @Test
    void extract_withBlankModelExecutionModel_throwsResponseInvalid() {
        String modelExecutions = """
                [
                  {"stage":"CORE_EXTRACTION","provider":"OLLAMA","model":""},
                  {"stage":"RESPONSIBILITY_EXTRACTION","provider":"OLLAMA","model":"resp-model"}
                ]
                """;
        expectExtractCall().andRespond(withSuccess(
                envelope(JOB_POSTING_ID, EXTRACTION_TASK_ID, "EXTRACTED", modelExecutions),
                MediaType.APPLICATION_JSON
        ));

        assertThatFailsWithResponseInvalid();
    }

    private void assertThatFailsWithResponseInvalid() {
        assertThatThrownBy(() -> client.extract(JOB_POSTING_ID, EXTRACTION_TASK_ID, "채용공고 본문"))
                .isInstanceOf(PythonExtractionException.class)
                .satisfies(exception -> assertThat(
                        ((PythonExtractionException) exception).getFailure())
                        .isEqualTo(PythonExtractionFailure.RESPONSE_INVALID));
    }

    private ResponseActions expectExtractCall() {
        return server.expect(requestTo("http://python-worker.test/internal/v1/job-postings/extract"));
    }

    private String envelope(
            String jobPostingId,
            String extractionTaskId,
            String status,
            String modelExecutionsJson
    ) {
        return """
                {
                  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
                  "data": {
                    "jobPostingId": "%s",
                    "extractionTaskId": "%s",
                    "status": "%s",
                    "extraction": {},
                    "modelExecutions": %s
                  },
                  "error": null,
                  "timestamp": "2026-07-30T08:00:00Z"
                }
                """.formatted(jobPostingId, extractionTaskId, status, modelExecutionsJson);
    }
}
