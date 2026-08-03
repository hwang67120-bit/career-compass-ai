package com.careercompass.userprofile.dto;

import java.util.UUID;

import com.careercompass.userprofile.domain.UserProfileTechnologyTagSourceType;

public record UserProfileTechnologyTagResponse(
        UUID technologyTagId,
        String rawName,
        String normalizedName,
        String displayName,
        UserProfileTechnologyTagSourceType sourceType
) {
}
