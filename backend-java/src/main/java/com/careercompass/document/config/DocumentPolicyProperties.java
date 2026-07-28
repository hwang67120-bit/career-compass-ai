package com.careercompass.document.config;

import com.careercompass.document.exception.DocumentTextTooLargeException;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "document.policy")
public record DocumentPolicyProperties(@Min(1) int maxTextLength) {

    /**
     * 기능: 문서 원문의 길이가 운영 설정 범위인지 검증한다.
     */
    public void validateText(String text) {
        if (text.length() > maxTextLength) {
            throw new DocumentTextTooLargeException();
        }
    }
}