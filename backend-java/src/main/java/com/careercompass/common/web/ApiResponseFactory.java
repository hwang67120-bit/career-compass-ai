package com.careercompass.common.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.careercompass.common.observability.RequestCorrelationContext;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    private final Clock clock;

    public ApiResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public <T> ApiResponse<T> success(T responseBody) {
        return new ApiResponse<>(requestId(), responseBody, null, OffsetDateTime.now(clock));
    }

    public ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(requestId(), null, error, OffsetDateTime.now(clock));
    }

    private UUID requestId() {
        return RequestCorrelationContext.currentOrCreate();
    }
}
