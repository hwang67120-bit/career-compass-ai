package com.careercompass.document.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BasicPersonalInformationSanitizerTest {

    private final BasicPersonalInformationSanitizer sanitizer = new BasicPersonalInformationSanitizer();

    @Test
    void sanitize_withContactAndResidentNumber_masksPersonalInformation() {
        String text = "이메일 user@example.com, 전화 010-1234-5678, 주민번호 990101-1234567, 기술 Java 21";

        String sanitizedText = sanitizer.sanitize(text);

        assertThat(sanitizedText)
                .doesNotContain("user@example.com", "010-1234-5678", "990101-1234567")
                .contains("[EMAIL]", "[PHONE]", "[RESIDENT_REGISTRATION_NUMBER]", "Java 21");
    }
}