package com.careercompass.common.web;

public record FieldErrorDetail(
        String fieldName,
        String message
) {
}