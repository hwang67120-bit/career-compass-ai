package com.careercompass.pythonworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "python.worker")
public record PythonWorkerProperties(String baseUrl) {
}
