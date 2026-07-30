package com.careercompass.pythonworker.dto;

public record PythonStatusResponse(
        boolean connected,
        String status,
        Boolean modelReady
) {
}
