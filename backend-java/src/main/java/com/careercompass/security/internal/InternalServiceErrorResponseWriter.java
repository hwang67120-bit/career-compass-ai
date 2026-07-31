package com.careercompass.security.internal;

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
public class InternalServiceErrorResponseWriter {

    private final ApiResponseFactory responseFactory;
    private final ObjectMapper objectMapper;

    public InternalServiceErrorResponseWriter(
            ApiResponseFactory responseFactory,
            ObjectMapper objectMapper
    ) {
        this.responseFactory = responseFactory;
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorized(HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        ApiError error = new ApiError(
                "INTERNAL_UNAUTHORIZED",
                "내부 서비스 인증에 실패했습니다.",
                List.of(),
                false
        );
        objectMapper.writeValue(
                response.getOutputStream(),
                responseFactory.failure(error)
        );
    }
}
