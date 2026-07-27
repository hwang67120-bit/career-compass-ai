package com.careercompass.pythonworker.client;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonHealthClient {

    private final RestClient restClient;

    public PythonHealthClient(
            RestClient.Builder builder,
            PythonWorkerProperties properties
    ) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
    }

    public PythonHealthResponse getHealth() {
        return restClient.get()
                .uri("/internal/v1/health")
                .retrieve()
                .body(PythonHealthResponse.class);
    }
}
