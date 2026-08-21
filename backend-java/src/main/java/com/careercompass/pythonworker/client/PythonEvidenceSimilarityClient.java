package com.careercompass.pythonworker.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.careercompass.common.observability.RequestCorrelationContext;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityEnvelope;
import com.careercompass.pythonworker.dto.PythonEvidenceSimilarityRequest;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityException;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityFailure;
import com.careercompass.pythonworker.exception.PythonEvidenceSimilarityResponseViolation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonEvidenceSimilarityClient {

    private static final int MAX_JOB_EVIDENCE_COUNT = 20;
    private static final int MAX_USER_EVIDENCE_COUNT = 30;
    private static final int MAX_TEXT_CODE_POINTS = 500;
    private static final Set<String> ALLOWED_OVERALL_STATUSES =
            Set.of("CALCULATED", "NOT_CALCULABLE");
    private static final Set<String> ALLOWED_PROVIDERS = Set.of("OLLAMA");

    private final RestClient restClient;
    private final String internalServiceToken;

    public PythonEvidenceSimilarityClient(
            @Qualifier("pythonJobPostingExtractionRestClient") RestClient restClient,
            PythonWorkerProperties properties
    ) {
        this.restClient = restClient;
        this.internalServiceToken = properties.internalToken();
    }

    /**
     * 기능: 채용공고 담당 업무와 사용자가 확인한 프로젝트 업무 근거를 Python에 전달해 비교한다.
     * 반환 값: 저장 전 계약 검증을 통과한 항목별 의미 비교 결과를 반환한다.
     */
    public PythonEvidenceSimilarityEnvelope.Data compare(
            PythonEvidenceSimilarityRequest request
    ) {
        validateRequest(request);
        PythonEvidenceSimilarityEnvelope envelope;
        try {
            envelope = restClient.post()
                    .uri("/internal/v1/job-evidence-similarities")
                    .header(PythonWorkerRequestHeaders.INTERNAL_TOKEN, internalServiceToken)
                    .header(PythonWorkerRequestHeaders.REQUEST_ID,
                            RequestCorrelationContext.currentOrCreate().toString())
                    .body(request)
                    .exchange((httpRequest, response) ->
                            response.bodyTo(PythonEvidenceSimilarityEnvelope.class));
        } catch (RestClientException exception) {
            throw new PythonEvidenceSimilarityException(
                    PythonEvidenceSimilarityFailure.MODEL_UNAVAILABLE, exception);
        }
        if (envelope == null || envelope.data() == null) {
            throw failureFromEnvelope(envelope);
        }
        validateData(request, envelope.data());
        return envelope.data();
    }

    private void validateRequest(PythonEvidenceSimilarityRequest request) {
        if (request == null || isBlank(request.comparisonTaskId())
                || isBlank(request.jobAnalysisId()) || isBlank(request.jobPostingId())
                || request.jobEvidence() == null || request.jobEvidence().isEmpty()
                || request.jobEvidence().size() > MAX_JOB_EVIDENCE_COUNT
                || request.userEvidence() == null
                || request.userEvidence().size() > MAX_USER_EVIDENCE_COUNT) {
            throw requestInvalid();
        }
        Set<String> evidenceIds = new HashSet<>();
        for (PythonEvidenceSimilarityRequest.JobEvidence evidence : request.jobEvidence()) {
            if (evidence == null || !"RESPONSIBILITY".equals(evidence.category())
                    || !isValidEvidence(evidence.evidenceId(), evidence.text(), evidenceIds)) {
                throw requestInvalid();
            }
        }
        for (PythonEvidenceSimilarityRequest.UserEvidence evidence : request.userEvidence()) {
            if (evidence == null || isBlank(evidence.projectSourceId())
                    || !"PROJECT_RESPONSIBILITY".equals(evidence.category())
                    || !isValidEvidence(evidence.evidenceId(), evidence.text(), evidenceIds)) {
                throw requestInvalid();
            }
        }
    }

    private boolean isValidEvidence(String evidenceId, String text, Set<String> evidenceIds) {
        return !isBlank(evidenceId) && !isBlank(text) && evidenceIds.add(evidenceId)
                && text.codePointCount(0, text.length()) <= MAX_TEXT_CODE_POINTS;
    }

    private void validateData(
            PythonEvidenceSimilarityRequest request,
            PythonEvidenceSimilarityEnvelope.Data data
    ) {
        if (!request.comparisonTaskId().equals(data.comparisonTaskId())
                || !request.jobAnalysisId().equals(data.jobAnalysisId())
                || !request.jobPostingId().equals(data.jobPostingId())) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.IDENTIFIER_MISMATCH);
        }
        if (!ALLOWED_OVERALL_STATUSES.contains(data.status())) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.STATUS_INVALID);
        }
        if (!"LLM_JUDGE".equals(data.method())) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.METHOD_INVALID);
        }
        if (data.results() == null
                || data.results().size() != request.jobEvidence().size()) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.RESULT_COUNT_INVALID);
        }
        Set<String> expectedJobIds = request.jobEvidence().stream()
                .map(PythonEvidenceSimilarityRequest.JobEvidence::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> userIds = request.userEvidence().stream()
                .map(PythonEvidenceSimilarityRequest.UserEvidence::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> returnedJobIds = new HashSet<>();
        int calculatedCount = 0;
        for (PythonEvidenceSimilarityEnvelope.Result result : data.results()) {
            if (result == null || !expectedJobIds.contains(result.jobEvidenceId())
                    || !returnedJobIds.add(result.jobEvidenceId())) {
                throw responseInvalid(
                        PythonEvidenceSimilarityResponseViolation.RESULT_REFERENCE_INVALID);
            }
            if ("CALCULATED".equals(result.status())) {
                calculatedCount++;
                validateCalculatedResult(result, userIds);
            } else if ("NOT_CALCULABLE".equals(result.status())) {
                validateNotCalculableResult(result);
            } else {
                throw responseInvalid(PythonEvidenceSimilarityResponseViolation.STATUS_INVALID);
            }
        }
        validateOverallStatus(data.status(), calculatedCount, data.results().size());
        validateModelExecution(data.modelExecution());
    }

    private void validateCalculatedResult(
            PythonEvidenceSimilarityEnvelope.Result result,
            Set<String> userIds
    ) {
        boolean judgmentValid = "RELATED".equals(result.judgment())
                || "NOT_RELATED".equals(result.judgment());
        boolean bestMatchValid = result.bestMatchUserEvidenceId() == null
                || userIds.contains(result.bestMatchUserEvidenceId());
        if (!judgmentValid || !bestMatchValid || result.score() != null
                || result.unavailableReason() != null
                || ("RELATED".equals(result.judgment())
                    && result.bestMatchUserEvidenceId() == null)
                || ("NOT_RELATED".equals(result.judgment())
                    && result.bestMatchUserEvidenceId() != null)) {
            throw responseInvalid(
                    PythonEvidenceSimilarityResponseViolation.RESULT_COMBINATION_INVALID);
        }
    }

    private void validateNotCalculableResult(PythonEvidenceSimilarityEnvelope.Result result) {
        if (result.bestMatchUserEvidenceId() != null || result.score() != null
                || result.judgment() != null
                || !"COMPATIBLE_USER_EVIDENCE_MISSING".equals(result.unavailableReason())) {
            throw responseInvalid(
                    PythonEvidenceSimilarityResponseViolation.RESULT_COMBINATION_INVALID);
        }
    }

    private void validateOverallStatus(String status, int calculatedCount, int totalCount) {
        if (calculatedCount > 0 && calculatedCount < totalCount) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.STATUS_INVALID);
        }
        String expected = calculatedCount == totalCount
                ? "CALCULATED"
                : "NOT_CALCULABLE";
        if (!expected.equals(status)) {
            throw responseInvalid(PythonEvidenceSimilarityResponseViolation.STATUS_INVALID);
        }
    }

    private void validateModelExecution(PythonEvidenceSimilarityEnvelope.ModelExecution execution) {
        if (execution == null || !"EVIDENCE_SEMANTIC_COMPARISON".equals(execution.stage())
                || !ALLOWED_PROVIDERS.contains(execution.provider())
                || isBlank(execution.model())) {
            throw responseInvalid(
                    PythonEvidenceSimilarityResponseViolation.MODEL_EXECUTION_INVALID);
        }
    }

    private PythonEvidenceSimilarityException failureFromEnvelope(
            PythonEvidenceSimilarityEnvelope envelope
    ) {
        String errorType = envelope != null && envelope.error() != null
                ? envelope.error().errorType() : null;
        if ("SEMANTIC_COMPARISON_MODEL_UNAVAILABLE".equals(errorType)) {
            return new PythonEvidenceSimilarityException(
                    PythonEvidenceSimilarityFailure.MODEL_UNAVAILABLE);
        }
        if ("INVALID_SIMILARITY_REQUEST".equals(errorType)
                || "INTERNAL_UNAUTHORIZED".equals(errorType)
                || "INTERNAL_TOKEN_REQUIRED".equals(errorType)) {
            return requestInvalid();
        }
        return new PythonEvidenceSimilarityException(
                PythonEvidenceSimilarityFailure.RESPONSE_INVALID);
    }

    private PythonEvidenceSimilarityException requestInvalid() {
        return new PythonEvidenceSimilarityException(
                PythonEvidenceSimilarityFailure.REQUEST_INVALID);
    }

    private PythonEvidenceSimilarityException responseInvalid(
            PythonEvidenceSimilarityResponseViolation violation
    ) {
        return new PythonEvidenceSimilarityException(
                PythonEvidenceSimilarityFailure.RESPONSE_INVALID, violation);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
