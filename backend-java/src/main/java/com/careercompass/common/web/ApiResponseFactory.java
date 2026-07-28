package com.careercompass.common.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    private final Clock clock;

    public ApiResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(UUID.randomUUID(), data, null, OffsetDateTime.now(clock));
    }

    public ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(UUID.randomUUID(), null, error, OffsetDateTime.now(clock));
    }
}