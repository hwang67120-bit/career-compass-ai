package com.careercompass.userprofile.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID userProfileId,
        int version,
        String targetJobTitle,
        List<UserProfileTechnologyTagResponse> technologyTags,
        Instant updatedAt
) {
}
