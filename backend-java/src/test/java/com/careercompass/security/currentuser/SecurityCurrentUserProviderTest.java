package com.careercompass.security.currentuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityCurrentUserProviderTest {

    private final SecurityCurrentUserProvider provider = new SecurityCurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_withUuidSubject_returnsUserId() {
        UUID userId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(userId.toString(), null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(provider.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    void getCurrentUserId_withoutAuthentication_throwsUnavailableException() {
        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(CurrentUserUnavailableException.class);
    }

    @Test
    void getCurrentUserId_withUnauthenticatedToken_throwsUnavailableException() {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("anonymous", null);
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(CurrentUserUnavailableException.class);
    }

    @Test
    void getCurrentUserId_withNonUuidSubject_throwsUnavailableException() {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("not-a-uuid", null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(CurrentUserUnavailableException.class);
    }
}
