package com.careercompass.security.currentuser;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "prod"})
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CurrentUserUnavailableException();
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new CurrentUserUnavailableException();
        }
    }
}