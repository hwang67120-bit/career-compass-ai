package com.careercompass.common.web;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiResponse<T>(
        UUID requestId,
        T data,
        ApiError error,
        OffsetDateTime timestamp
) {
}