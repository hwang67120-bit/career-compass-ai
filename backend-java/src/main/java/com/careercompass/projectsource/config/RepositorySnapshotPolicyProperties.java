package com.careercompass.projectsource.config;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "github.snapshot")
@Validated
public record RepositorySnapshotPolicyProperties(
        @Min(1) long maxRemoteFileBytes,
        @Min(1) int maxSourceAndTestFilesPerTechnology,
        @NotEmpty List<String> excludedPathSegments,
        @NotEmpty List<String> excludedFileNameFragments,
        @NotEmpty List<String> manifestFileNames,
        @NotEmpty List<String> configurationFileNames,
        @NotEmpty List<String> sourceExtensions
) {
}
