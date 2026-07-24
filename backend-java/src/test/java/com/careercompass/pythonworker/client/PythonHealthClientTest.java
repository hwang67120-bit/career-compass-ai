package com.careercompass.pythonworker.client;

import com.careercompass.pythonworker.config.PythonWorkerProperties;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(PythonHealthClient.class)
@EnableConfigurationProperties(PythonWorkerProperties.class)
@TestPropertySource(properties = "python.worker.base-url=http://python-worker.test")
class PythonHealthClientTest {

    @Autowired
    private PythonHealthClient pythonHealthClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void getHealth_returnsParsedResponse_whenPythonRespondsSuccessfully() {
        server.expect(requestTo("http://python-worker.test/internal/v1/health"))
                .andRespond(withSuccess(
                        "{\"status\":\"UP\",\"model_ready\":false}",
                        MediaType.APPLICATION_JSON
                ));

        PythonHealthResponse response = pythonHealthClient.getHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.modelReady()).isFalse();
    }

    @Test
    void getHealth_throwsRestClientResponseException_whenPythonRespondsWithServerError() {
        server.expect(requestTo("http://python-worker.test/internal/v1/health"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> pythonHealthClient.getHealth())
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void getHealth_throwsResourceAccessException_whenConnectionFails() {
        server.expect(requestTo("http://python-worker.test/internal/v1/health"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> pythonHealthClient.getHealth())
                .isInstanceOf(ResourceAccessException.class);
    }
}
