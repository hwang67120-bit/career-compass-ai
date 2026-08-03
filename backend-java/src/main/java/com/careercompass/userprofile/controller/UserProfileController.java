package com.careercompass.userprofile.controller;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.userprofile.dto.SaveUserProfileRequest;
import com.careercompass.userprofile.dto.UserProfileResponse;
import com.careercompass.userprofile.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final ApiResponseFactory responseFactory;

    public UserProfileController(
            UserProfileService userProfileService,
            ApiResponseFactory responseFactory
    ) {
        this.userProfileService = userProfileService;
        this.responseFactory = responseFactory;
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> saveUserProfile(
            @RequestBody SaveUserProfileRequest request
    ) {
        return responseFactory.success(
                userProfileService.saveCurrentUserProfile(request)
        );
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> retrieveUserProfile() {
        return responseFactory.success(
                userProfileService.retrieveCurrentUserProfile()
        );
    }
}
