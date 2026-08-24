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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "external_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_identity_provider_user",
                columnNames = {"provider", "provider_user_id"}
        )
)
public class ExternalIdentity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 30)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false, length = 255)
    private String providerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected ExternalIdentity() {
    }

    private ExternalIdentity(
            UUID id,
            UUID userId,
            OAuthProvider provider,
            String providerUserId,
            Instant createdAt,
            Instant lastLoginAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.provider = Objects.requireNonNull(provider);
        this.providerUserId = requireProviderUserId(providerUserId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastLoginAt = Objects.requireNonNull(lastLoginAt);
    }

    public static ExternalIdentity create(
            UUID id,
            UUID userId,
            OAuthProvider provider,
            String providerUserId,
            Instant createdAt
    ) {
        return new ExternalIdentity(
                id, userId, provider, providerUserId, createdAt, createdAt);
    }

    public void recordLogin(Instant loggedInAt) {
        lastLoginAt = Objects.requireNonNull(loggedInAt);
    }

    private static String requireProviderUserId(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("외부 제공자 사용자 식별자는 필수입니다.");
        }
        return providerUserId.strip();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OAuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
