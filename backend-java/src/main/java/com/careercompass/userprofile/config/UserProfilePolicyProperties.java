package com.careercompass.userprofile.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user.profile")
public record UserProfilePolicyProperties(
        @Min(1) int maxTargetJobTitleLength,
        @Min(1) int maxTechnologyTagCount,
        @Min(1) int maxCustomTagNameLength
) {
}
