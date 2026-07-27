package com.careercompass.document.privacy;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class BasicPersonalInformationSanitizer implements DocumentTextSanitizer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![\\w.!#$%&'*+/=?^`{|}~-])[\\w.!#$%&'*+/=?^`{|}~-]+@[\\w-]+(?:\\.[\\w-]+)+(?![\\w-])"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:01[016789]|0\\d{1,2})[- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)"
    );
    private static final Pattern RESIDENT_NUMBER_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)"
    );

    @Override
    public String sanitize(String text) {
        String sanitizedText = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL]");
        sanitizedText = PHONE_PATTERN.matcher(sanitizedText).replaceAll("[PHONE]");
        return RESIDENT_NUMBER_PATTERN.matcher(sanitizedText)
                .replaceAll("[RESIDENT_REGISTRATION_NUMBER]");
    }
}