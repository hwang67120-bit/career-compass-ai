package com.careercompass.security.currentuser;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestCurrentUserProvider implements CurrentUserProvider {

    private final UUID userId;

    public TestCurrentUserProvider(@Value("${test.user-id}") UUID userId) {
        this.userId = userId;
    }

    @Override
    public UUID getCurrentUserId() {
        return userId;
    }
}