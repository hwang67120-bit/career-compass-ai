package com.careercompass.userprofile.dto;

import java.util.List;

public record SaveUserProfileRequest(
        Integer expectedVersion,
        String targetJobTitle,
        List<UserProfileTechnologyTagRequest> technologyTags
) {
}
