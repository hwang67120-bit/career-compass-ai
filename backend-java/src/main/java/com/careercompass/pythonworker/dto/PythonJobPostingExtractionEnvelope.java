package com.careercompass.pythonworker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 계약: contracts/job-posting-extraction.md 4·5절. 프로젝트 공통 응답 봉투 형식이다.
 * `extraction`·`modelExecutions`는 이번 범위에서 Java가 값을 직접 조작하지 않고 그대로
 * 저장·조회에만 쓴다. Jackson 특정 타입(JsonNode 등)이 아니라 순수 Map·List로 받는다 —
 * 이 프로젝트가 스프링 내부적으로 자동 구성하는 Jackson(tools.jackson, 3.x)과 이
 * 클라이언트가 쓰는 Jackson(com.fasterxml.jackson, 2.x)이 서로 다른 라이브러리라서,
 * 두 쪽 모두가 그대로 다룰 수 있는 JDK 표준 타입만 쓴다(확인 필요 — 코덱스가 정식으로
 * Jackson 버전을 정리할 때 재검토).
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
            Object extraction,
            Object modelExecutions
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
