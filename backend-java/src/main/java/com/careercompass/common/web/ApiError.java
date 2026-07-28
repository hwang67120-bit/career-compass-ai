package com.careercompass.common.web;

import java.util.List;

public record ApiError(
        String errorType,
        String message,
        List<FieldErrorDetail> fieldErrors,
        boolean retryable
) {
}