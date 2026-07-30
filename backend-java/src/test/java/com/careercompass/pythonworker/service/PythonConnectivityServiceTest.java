package com.careercompass.pythonworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.careercompass.pythonworker.client.PythonHealthClient;
import com.careercompass.pythonworker.dto.PythonHealthResponse;
import com.careercompass.pythonworker.dto.PythonStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.ResourceAccessException;

class PythonConnectivityServiceTest {

    private PythonHealthClient pythonHealthClient;
    private PythonConnectivityService service;

    @BeforeEach
    void setUp() {
        pythonHealthClient = Mockito.mock(PythonHealthClient.class);
        service = new PythonConnectivityService(pythonHealthClient);
    }

    @Test
    void checkPythonConnectivity_whenHealthResponds_returnsConnectedStatus() {
        when(pythonHealthClient.getHealth())
                .thenReturn(new PythonHealthResponse("UP", true));

        PythonStatusResponse response = service.checkPythonConnectivity();

        assertThat(response.connected()).isTrue();
        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.modelReady()).isTrue();
    }

    @Test
    void checkPythonConnectivity_whenConnectionFails_returnsDisconnectedStatus() {
        when(pythonHealthClient.getHealth())
                .thenThrow(new ResourceAccessException("Connection refused"));

        PythonStatusResponse response = service.checkPythonConnectivity();

        assertThat(response.connected()).isFalse();
        assertThat(response.status()).isNull();
        assertThat(response.modelReady()).isNull();
    }

    @Test
    void checkPythonConnectivity_whenResponseBodyIsEmpty_returnsDisconnectedStatus() {
        when(pythonHealthClient.getHealth()).thenReturn(null);

        PythonStatusResponse response = service.checkPythonConnectivity();

        assertThat(response.connected()).isFalse();
        assertThat(response.status()).isNull();
        assertThat(response.modelReady()).isNull();
    }
}
