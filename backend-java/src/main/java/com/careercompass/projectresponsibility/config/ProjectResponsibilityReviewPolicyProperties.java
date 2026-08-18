package com.careercompass.projectresponsibility.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "project-responsibility.review")
@Validated
public record ProjectResponsibilityReviewPolicyProperties(
        @Min(1) int candidateRetentionDays
) {
}
