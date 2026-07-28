package com.careercompass.security.controller;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.security.currentuser.CurrentUserProvider;
import com.careercompass.security.dto.CurrentUserResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"dev", "test"})
public class DevelopmentAuthController {

    private final CurrentUserProvider currentUserProvider;
    private final ApiResponseFactory responseFactory;

    public DevelopmentAuthController(
            CurrentUserProvider currentUserProvider,
            ApiResponseFactory responseFactory
    ) {
        this.currentUserProvider = currentUserProvider;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser() {
        return responseFactory.success(new CurrentUserResponse(
                true, currentUserProvider.getCurrentUserId()));
    }
}
