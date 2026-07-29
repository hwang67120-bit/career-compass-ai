package com.careercompass.pythonworker.dto;

import com.careercompass.common.web.ApiError;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentExtractionErrorResponse(
        UUID requestId,
        Void data,
        ApiError error,
        OffsetDateTime timestamp
) {
}
