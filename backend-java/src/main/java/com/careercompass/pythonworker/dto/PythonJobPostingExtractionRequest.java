package com.careercompass.pythonworker.dto;

/**
 * 계약: contracts/job-posting-extraction.md 3절. Java가 생성한 식별자와 Java가 이미
 * 연락처·HTML을 제거한 최소 sourceText를 그대로 전달한다.
 */
public record PythonJobPostingExtractionRequest(
        String jobPostingId,
        String extractionTaskId,
        String sourceText
) {
}
