package com.careercompass.pythonworker.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * `extractConnectTimeout`·`extractReadTimeout`은 채용공고 추출 호출
 * (PythonJobPostingExtractionClient) 전용이다 — 워커 스레드 안에서 실행되므로 Python·LLM이
 * 멈추면 분석이 RUNNING에 그대로 고정되는 것을 막는다(PR #48 리뷰 반영). 헬스체크
 * (PythonHealthClient)는 사용자가 직접 기다리는 짧은 호출이라 별도 제한시간을 두지 않는다.
 */
@ConfigurationProperties(prefix = "python.worker")
@Validated
public record PythonWorkerProperties(
        @NotBlank String baseUrl,
        @NotBlank String internalToken,
        @NotNull Duration extractConnectTimeout,
        @NotNull Duration extractReadTimeout
) {
}
