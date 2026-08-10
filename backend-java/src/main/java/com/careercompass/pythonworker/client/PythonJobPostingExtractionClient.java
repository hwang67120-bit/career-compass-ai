package com.careercompass.pythonworker.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.careercompass.common.observability.RequestCorrelationContext;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionRequest;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonExtractionFailure;
import com.careercompass.pythonworker.exception.PythonExtractionResponseViolation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 계약: contracts/job-posting-extraction.md. Java가 채용공고 원문을 Python에 보내
 * 직무명·기술·담당 업무를 구조화 추출받는다.
 */
@Component
public class PythonJobPostingExtractionClient {

    private static final String EXTRACTED_STATUS = "EXTRACTED";
    private static final Set<String> EXPECTED_MODEL_EXECUTION_STAGES =
            Set.of("CORE_EXTRACTION", "RESPONSIBILITY_EXTRACTION");
    private static final Set<String> ALLOWED_MODEL_EXECUTION_PROVIDERS =
            Set.of("OLLAMA", "GEMINI");

    private final RestClient restClient;
    private final String internalServiceToken;

    public PythonJobPostingExtractionClient(
            @Qualifier("pythonJobPostingExtractionRestClient") RestClient restClient,
            PythonWorkerProperties properties
    ) {
        this.restClient = restClient;
        this.internalServiceToken = properties.internalToken();
    }

    /**
     * 기능: 채용공고 원문을 구조화 추출한다.
     * 반환 값: 추출 결과(extraction)와 단계별 모델 실행 이력(modelExecutions)을 반환한다.
     */
    public PythonJobPostingExtractionEnvelope.Data extract(
            String jobPostingId,
            String extractionTaskId,
            String sourceText
    ) {
        PythonJobPostingExtractionEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri("/internal/v1/job-postings/extract")
                    .header(PythonWorkerRequestHeaders.INTERNAL_TOKEN, internalServiceToken)
                    .header(PythonWorkerRequestHeaders.REQUEST_ID,
                            RequestCorrelationContext.currentOrCreate().toString())
                    .body(new PythonJobPostingExtractionRequest(
                            jobPostingId, extractionTaskId, sourceText))
                    .exchange((request, response) -> response.bodyTo(
                            PythonJobPostingExtractionEnvelope.class));
        } catch (RestClientException exception) {
            throw new PythonExtractionException(PythonExtractionFailure.UNAVAILABLE, exception);
        }

        if (envelope == null || envelope.data() == null) {
            throw failureFromEnvelope(envelope);
        }
        validateData(jobPostingId, extractionTaskId, envelope.data());
        return envelope.data();
    }

    /**
     * 기능: Python 응답을 계약 스키마로 다시 검증한다(계약 2절 Java 책임). 요청과
     * 다른 jobPostingId·extractionTaskId, EXTRACTED가 아닌 상태, 빈 extraction이나
     * modelExecutions의 stage·provider·model 오류는 저장 전에 걸러낸다.
     */
    private void validateData(
            String requestedJobPostingId,
            String requestedExtractionTaskId,
            PythonJobPostingExtractionEnvelope.Data data
    ) {
        if (!requestedJobPostingId.equals(data.jobPostingId())) {
            throw responseInvalid(PythonExtractionResponseViolation.JOB_POSTING_ID_MISMATCH);
        }
        if (!requestedExtractionTaskId.equals(data.extractionTaskId())) {
            throw responseInvalid(PythonExtractionResponseViolation.EXTRACTION_TASK_ID_MISMATCH);
        }
        if (!EXTRACTED_STATUS.equals(data.status())) {
            throw responseInvalid(PythonExtractionResponseViolation.STATUS_INVALID);
        }
        if (data.extraction() == null) {
            throw responseInvalid(PythonExtractionResponseViolation.EXTRACTION_MISSING);
        }
        if (!hasExpectedModelExecutions(data.modelExecutions())) {
            throw responseInvalid(PythonExtractionResponseViolation.MODEL_EXECUTIONS_INVALID);
        }
    }

    private boolean hasExpectedModelExecutions(
            List<PythonJobPostingExtractionEnvelope.ModelExecution> modelExecutions
    ) {
        if (modelExecutions == null) {
            return false;
        }
        Set<String> stages = new HashSet<>();
        for (PythonJobPostingExtractionEnvelope.ModelExecution execution : modelExecutions) {
            if (execution == null || execution.stage() == null || !stages.add(execution.stage())) {
                return false;
            }
            if (!ALLOWED_MODEL_EXECUTION_PROVIDERS.contains(execution.provider())) {
                return false;
            }
            if (execution.model() == null || execution.model().isBlank()) {
                return false;
            }
        }
        return stages.equals(EXPECTED_MODEL_EXECUTION_STAGES);
    }

    private PythonExtractionException responseInvalid(
            PythonExtractionResponseViolation responseViolation
    ) {
        return new PythonExtractionException(
                PythonExtractionFailure.RESPONSE_INVALID,
                responseViolation
        );
    }

    private PythonExtractionException failureFromEnvelope(
            PythonJobPostingExtractionEnvelope envelope
    ) {
        String errorType = envelope != null && envelope.error() != null
                ? envelope.error().errorType()
                : null;
        if ("MODEL_UNAVAILABLE".equals(errorType)) {
            return new PythonExtractionException(PythonExtractionFailure.UNAVAILABLE);
        }
        if ("INVALID_EXTRACTION_REQUEST".equals(errorType)
                || "INTERNAL_TOKEN_REQUIRED".equals(errorType)
                || "INTERNAL_UNAUTHORIZED".equals(errorType)) {
            return new PythonExtractionException(PythonExtractionFailure.REQUEST_INVALID);
        }
        return new PythonExtractionException(PythonExtractionFailure.RESPONSE_INVALID);
    }
}
