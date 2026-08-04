package com.careercompass.pythonworker.client;

import com.careercompass.common.observability.RequestCorrelationContext;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionEnvelope;
import com.careercompass.pythonworker.dto.PythonJobPostingExtractionRequest;
import com.careercompass.pythonworker.exception.PythonExtractionException;
import com.careercompass.pythonworker.exception.PythonExtractionFailure;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 계약: contracts/job-posting-extraction.md. Java가 채용공고 원문을 Python에 보내
 * 직무명·기술·담당 업무를 구조화 추출받는다.
 */
@Component
public class PythonJobPostingExtractionClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public PythonJobPostingExtractionClient(
            RestClient.Builder builder,
            PythonWorkerProperties properties
    ) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
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
        return envelope.data();
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
