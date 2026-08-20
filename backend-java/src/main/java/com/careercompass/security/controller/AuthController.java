package com.careercompass.security.controller;

import java.util.UUID;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.security.dto.CsrfTokenResponse;
import com.careercompass.security.dto.CurrentUserResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile({"dev", "prod"})
public class AuthController {

    private final ApiResponseFactory responseFactory;

    public AuthController(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return responseFactory.success(new CurrentUserResponse(false, null));
        }

        String authenticationName = authentication.getName();
        if (authenticationName == null) {
            return responseFactory.success(new CurrentUserResponse(false, null));
        }
        UUID userId;
        try {
            userId = UUID.fromString(authenticationName);
        } catch (IllegalArgumentException exception) {
            return responseFactory.success(new CurrentUserResponse(false, null));
        }
        return responseFactory.success(new CurrentUserResponse(true, userId));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> getCsrfToken(CsrfToken csrfToken) {
        return responseFactory.success(new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getToken()
        ));
    }
}
