package com.careercompass.pythonworker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PythonHealthResponse(
        String status,
        @JsonProperty("model_ready") boolean modelReady
) {
}
