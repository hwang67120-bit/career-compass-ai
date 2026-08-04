package com.careercompass.userprofile.dto;

import java.util.UUID;

public record UserProfileTechnologyTagRequest(
        UUID technologyTagId,
        String customName
) {
}
