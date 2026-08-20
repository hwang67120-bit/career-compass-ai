package com.careercompass.userprofile.repository;

import java.util.Optional;
import java.util.UUID;

import com.careercompass.userprofile.domain.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT userProfile FROM UserProfile userProfile WHERE userProfile.id = :id")
    Optional<UserProfile> findByIdForUpdate(@Param("id") UUID id);
}
