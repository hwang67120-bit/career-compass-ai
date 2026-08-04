package com.careercompass.pythonworker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 계약: contracts/job-posting-extraction.md 4·5절. 프로젝트 공통 응답 봉투 형식이다.
 * `extraction`·`modelExecutions`는 이번 범위에서 Java가 값을 직접 조작하지 않고 그대로
 * 저장·조회에만 쓰므로 JsonNode로 유연하게 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonJobPostingExtractionEnvelope(
        String requestId,
        Data data,
        Error error,
        String timestamp
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String jobPostingId,
            String extractionTaskId,
            String status,
            JsonNode extraction,
            JsonNode modelExecutions
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
            String errorType,
            String message,
            boolean retryable
    ) {
    }
}
