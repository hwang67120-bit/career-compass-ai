package com.careercompass.user.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 30)
    private UserStatus userStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    private UserAccount(UUID id, UserStatus userStatus, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userStatus = Objects.requireNonNull(userStatus);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static UserAccount create(UUID id, Instant createdAt) {
        return new UserAccount(id, UserStatus.ACTIVE, createdAt);
    }

    public boolean isActive() {
        return userStatus == UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
