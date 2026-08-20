package com.careercompass.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.careercompass.user.domain.ExternalIdentity;
import com.careercompass.user.domain.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    Optional<ExternalIdentity> findByProviderAndProviderUserId(
            OAuthProvider provider, String providerUserId);
}
