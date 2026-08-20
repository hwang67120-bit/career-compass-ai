package com.careercompass.pythonworker.controller;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.pythonworker.dto.PythonStatusResponse;
import com.careercompass.pythonworker.service.PythonConnectivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class PythonConnectivityController {

    private final PythonConnectivityService pythonConnectivityService;
    private final ApiResponseFactory responseFactory;

    public PythonConnectivityController(
            PythonConnectivityService pythonConnectivityService,
            ApiResponseFactory responseFactory
    ) {
        this.pythonConnectivityService = pythonConnectivityService;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/python-status")
    public ApiResponse<PythonStatusResponse> getPythonStatus() {
        return responseFactory.success(
                pythonConnectivityService.checkPythonConnectivity()
        );
    }
}
