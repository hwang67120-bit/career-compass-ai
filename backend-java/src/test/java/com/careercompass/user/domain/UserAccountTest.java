package com.careercompass.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserAccountTest {

    @Test
    void create_withIdentityAndTime_createsActiveUser() {
        UUID id = UUID.fromString("60000000-0000-0000-0000-000000000001");
        Instant createdAt = Instant.parse("2026-07-28T03:00:00Z");

        UserAccount user = UserAccount.create(id, createdAt);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.isActive()).isTrue();
    }
}
