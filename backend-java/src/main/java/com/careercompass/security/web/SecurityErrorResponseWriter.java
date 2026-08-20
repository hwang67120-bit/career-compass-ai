package com.careercompass.security.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.careercompass.common.web.ApiError;
import com.careercompass.common.web.ApiResponseFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorResponseWriter {

    private final ApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(
            ApiResponseFactory responseFactory,
            ObjectMapper objectMapper
    ) {
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    void writeUnauthorized(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }

    void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "요청한 작업을 수행할 권한이 없습니다.");
    }

    private void write(HttpServletResponse response, HttpStatus status,
                       String errorType, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        ApiError error = new ApiError(errorType, message, List.of(), false);
        objectMapper.writeValue(response.getOutputStream(), responseFactory.failure(error));
    }
}
