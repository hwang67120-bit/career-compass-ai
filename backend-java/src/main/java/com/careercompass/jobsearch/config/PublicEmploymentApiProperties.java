package com.careercompass.jobsearch.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "public-employment.api")
public record PublicEmploymentApiProperties(
        @NotNull URI baseUrl,
        String serviceKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Positive int maxSourceTextLength,
        @NotBlank String sortOrder
) {

    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "apis.data.go.kr";
    private static final String ALLOWED_PATH = "/1760000/PblJobService";

    public PublicEmploymentApiProperties {
        validateBaseUrl(baseUrl);
        validateTimeout(connectTimeout);
        validateTimeout(readTimeout);
    }

    /**
     * 기능: 공공취업 API 기본 주소가 허용된 공공데이터포털 HTTPS 주소인지 검증한다.
     */
    private static void validateBaseUrl(URI baseUrl) {
        boolean hasAllowedScheme = baseUrl != null
                && ALLOWED_SCHEME.equalsIgnoreCase(baseUrl.getScheme());
        boolean hasAllowedHost = baseUrl != null
                && baseUrl.getHost() != null
                && ALLOWED_HOST.equals(baseUrl.getHost().toLowerCase(Locale.ROOT));
        boolean hasAllowedPath = baseUrl != null
                && ALLOWED_PATH.equals(removeTrailingSlash(baseUrl.getPath()));
        boolean hasUnexpectedComponent = baseUrl != null
                && (baseUrl.getUserInfo() != null
                || baseUrl.getPort() != -1
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null);
        if (!hasAllowedScheme || !hasAllowedHost || !hasAllowedPath || hasUnexpectedComponent) {
            throw new IllegalArgumentException("허용되지 않은 공공취업 API 기본 주소입니다.");
        }
    }

    /**
     * 기능: 외부 API 제한시간이 양수인지 검증한다.
     */
    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("공공취업 API 제한시간은 양수여야 합니다.");
        }
    }

    private static String removeTrailingSlash(String path) {
        if (path != null && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
