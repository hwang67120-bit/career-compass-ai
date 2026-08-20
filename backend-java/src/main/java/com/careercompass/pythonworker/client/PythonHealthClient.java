package com.careercompass.pythonworker.client;

import com.careercompass.common.observability.RequestCorrelationContext;
import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonHealthClient {

    private final RestClient restClient;
    private final String internalServiceToken;

    public PythonHealthClient(
            RestClient.Builder builder,
            PythonWorkerProperties properties
    ) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
        this.internalServiceToken = properties.internalToken();
    }

    public PythonHealthResponse getHealth() {
        return restClient.get()
                .uri("/internal/v1/health")
                .header(PythonWorkerRequestHeaders.INTERNAL_TOKEN, internalServiceToken)
                .header(PythonWorkerRequestHeaders.REQUEST_ID,
                        RequestCorrelationContext.currentOrCreate().toString())
                .retrieve()
                .body(PythonHealthResponse.class);
    }
}
