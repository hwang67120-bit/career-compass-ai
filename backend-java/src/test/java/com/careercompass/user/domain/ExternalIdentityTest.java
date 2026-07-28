package com.careercompass.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExternalIdentityTest {

    private static final UUID ID =
            UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test
    void create_withGitHubId_createsIdentityAndInitialLoginTime() {
        Instant createdAt = Instant.parse("2026-07-28T03:00:00Z");

        ExternalIdentity identity = ExternalIdentity.create(
                ID, USER_ID, OAuthProvider.GITHUB, "583231", createdAt);

        assertThat(identity.getId()).isEqualTo(ID);
        assertThat(identity.getUserId()).isEqualTo(USER_ID);
        assertThat(identity.getProvider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(identity.getProviderUserId()).isEqualTo("583231");
        assertThat(identity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(identity.getLastLoginAt()).isEqualTo(createdAt);
    }

    @Test
    void recordLogin_withLaterTime_updatesLastLoginTime() {
        Instant createdAt = Instant.parse("2026-07-28T03:00:00Z");
        Instant loggedInAt = Instant.parse("2026-07-28T04:00:00Z");
        ExternalIdentity identity = ExternalIdentity.create(
                ID, USER_ID, OAuthProvider.GITHUB, "583231", createdAt);

        identity.recordLogin(loggedInAt);

        assertThat(identity.getLastLoginAt()).isEqualTo(loggedInAt);
    }

    @Test
    void create_withBlankProviderUserId_rejectsIdentity() {
        assertThatThrownBy(() -> ExternalIdentity.create(
                ID, USER_ID, OAuthProvider.GITHUB, " ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
