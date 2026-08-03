package com.careercompass.userprofile.repository;

import java.util.Optional;
import java.util.UUID;

import com.careercompass.userprofile.domain.UserProfileVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileVersionRepository
        extends JpaRepository<UserProfileVersion, UUID> {

    @EntityGraph(attributePaths = "technologyTags")
    Optional<UserProfileVersion> findByUserProfile_IdAndProfileVersion(
            UUID userProfileId, int profileVersion
    );
}
