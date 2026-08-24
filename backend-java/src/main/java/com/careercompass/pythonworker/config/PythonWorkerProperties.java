package com.careercompass.pythonworker.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * `extractConnectTimeout`·`extractReadTimeout`은 공고·프로젝트 근거 추출과 의미 비교처럼
 * LLM을 실행하는 내부 호출에 적용한다. 워커 스레드 안에서 실행되므로 Python·LLM이 멈추면
 * 분석이 RUNNING에 그대로 고정되는 것을 막는다(PR #48 리뷰 반영). 헬스체크
 * (PythonHealthClient)는 짧은 상태 확인 요청이므로 별도 제한시간을 사용한다.
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
