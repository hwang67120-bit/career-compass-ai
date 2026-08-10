package com.careercompass.jobsearch.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * `authKey`는 실제 채용정보 API 승인이 없어도(예: 개발 환경에서 DEV_SAMPLE Provider를
 * 쓸 때) 애플리케이션이 시작할 수 있어야 해서 필수 검증을 걸지 않는다 — 실제로 Work24를
 * 호출하는 시점({@link com.careercompass.jobsearch.client.Work24JobSearchClient})에서
 * 비어 있으면 그때 실패로 처리한다.
 */
@Validated
@ConfigurationProperties(prefix = "work24.api")
public record Work24ApiProperties(
        @NotNull URI baseUrl,
        String authKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {

    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "www.work24.go.kr";

    public Work24ApiProperties {
        validateBaseUrl(baseUrl);
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
    }

    /**
     * 기능: 외부 API 기본 주소가 허용된 고용24 HTTPS 호스트인지 검증한다.
     */
    private static void validateBaseUrl(URI baseUrl) {
        boolean hasAllowedScheme = baseUrl != null
                && ALLOWED_SCHEME.equalsIgnoreCase(baseUrl.getScheme());
        boolean hasAllowedHost = baseUrl != null
                && baseUrl.getHost() != null
                && ALLOWED_HOST.equals(baseUrl.getHost().toLowerCase(Locale.ROOT));
        boolean hasUnexpectedComponent = baseUrl != null
                && (baseUrl.getUserInfo() != null
                || baseUrl.getPort() != -1
                || (baseUrl.getPath() != null && !baseUrl.getPath().isBlank()
                && !"/".equals(baseUrl.getPath()))
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null);
        if (!hasAllowedScheme || !hasAllowedHost || hasUnexpectedComponent) {
            throw new IllegalArgumentException("허용되지 않은 고용24 API 기본 주소입니다.");
        }
    }

    /**
     * 기능: 외부 API 제한시간이 양수인지 검증한다.
     */
    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("고용24 API 제한시간은 양수여야 합니다.");
        }
    }
}
