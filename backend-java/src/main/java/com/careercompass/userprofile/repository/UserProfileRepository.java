package com.careercompass.userprofile.repository;

import java.util.Optional;
import java.util.UUID;

import com.careercompass.userprofile.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);
}
