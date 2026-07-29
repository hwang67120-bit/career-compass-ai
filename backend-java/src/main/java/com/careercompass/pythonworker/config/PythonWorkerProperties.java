package com.careercompass.pythonworker.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "python.worker")
@Validated
public record PythonWorkerProperties(
        @NotBlank String baseUrl,
        @NotBlank String internalToken
) {
}
