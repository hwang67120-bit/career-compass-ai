package com.careercompass.projectsource.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "github.api")
public record GitHubApiProperties(
        @NotNull URI baseUrl,
        @NotBlank String apiVersion,
        @NotBlank String userAgent,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        // 선택. 있으면 인증 요청(레이트 리밋 60→5000/시간), 없거나 비면 비인증(공개 저장소만).
        String token
) {

    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "api.github.com";

    public GitHubApiProperties {
        validateBaseUrl(baseUrl);
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
    }

    /**
     * 기능: 외부 API 기본 주소가 허용된 GitHub HTTPS 호스트인지 검증한다.
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
            throw new IllegalArgumentException("허용되지 않은 GitHub API 기본 주소입니다.");
        }
    }

    /**
     * 기능: 외부 API 제한시간이 양수인지 검증한다.
     */
    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("GitHub API 제한시간은 양수여야 합니다.");
        }
    }
}
