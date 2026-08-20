package com.careercompass.technologytag.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "technology-tag.resolution")
public record TechnologyTagResolutionPolicyProperties(
        @Min(1) int maxNames,
        @Min(1) int maxNameLength
) {
}
