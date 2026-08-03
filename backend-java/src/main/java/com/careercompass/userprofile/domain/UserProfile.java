package com.careercompass.userprofile.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    private UserProfile(UUID id, UUID userId, int currentVersion,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.currentVersion = currentVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserProfile create(UUID id, UUID userId, Instant createdAt) {
        return new UserProfile(id, userId, 1, createdAt, createdAt);
    }

    public void advanceVersion(int nextVersion, Instant updatedAt) {
        this.currentVersion = nextVersion;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
