package com.careercompass.security.dto;

import java.util.UUID;

public record CurrentUserResponse(
        boolean authenticated,
        UUID userId
) {
}
