package com.careercompass.pythonworker.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.pythonworker.dto.PythonStatusResponse;
import com.careercompass.pythonworker.service.PythonConnectivityService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PythonConnectivityControllerTest {

    private PythonConnectivityService pythonConnectivityService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pythonConnectivityService = Mockito.mock(PythonConnectivityService.class);
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                Clock.fixed(
                        Instant.parse("2026-07-30T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PythonConnectivityController(
                        pythonConnectivityService,
                        responseFactory
                )
        ).build();
    }

    @Test
    void getPythonStatus_whenConnected_returnsHealthDataInSuccessEnvelope()
            throws Exception {
        when(pythonConnectivityService.checkPythonConnectivity())
                .thenReturn(new PythonStatusResponse(true, "UP", false));

        mockMvc.perform(get("/api/v1/system/python-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.modelReady").value(false))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void getPythonStatus_whenDisconnected_returnsNullHealthFieldsInSuccessEnvelope()
            throws Exception {
        when(pythonConnectivityService.checkPythonConnectivity())
                .thenReturn(new PythonStatusResponse(false, null, null));

        mockMvc.perform(get("/api/v1/system/python-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected").value(false))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.modelReady").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
