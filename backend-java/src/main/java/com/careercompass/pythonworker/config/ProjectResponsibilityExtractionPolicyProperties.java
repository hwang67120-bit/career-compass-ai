package com.careercompass.pythonworker.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "python.worker.project-responsibility")
@Validated
public record ProjectResponsibilityExtractionPolicyProperties(
        @Min(1) int maxSelectedTechnologyTags,
        @Min(1) int maxDetectedTechnologies,
        @Min(1) int maxReadmes,
        @Min(1) int maxManifests,
        @Min(1) int maxConfigurations,
        @Min(1) int maxEvidenceItems,
        @Min(1) int maxEvidenceTextCodePoints,
        @Min(1) int maxTotalTextCodePoints,
        @Min(1) int maxResponsibilityTextCodePoints
) {
}
