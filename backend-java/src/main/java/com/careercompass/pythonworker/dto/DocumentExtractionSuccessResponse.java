package com.careercompass.pythonworker.dto;

import com.careercompass.common.web.ApiError;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentExtractionSuccessResponse(
        UUID requestId,
        DocumentExtractionData data,
        ApiError error,
        OffsetDateTime timestamp
) {
}
