package com.careercompass.security.dto;

public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
