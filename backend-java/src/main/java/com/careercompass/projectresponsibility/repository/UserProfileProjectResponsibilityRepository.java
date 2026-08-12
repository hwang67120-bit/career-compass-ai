package com.careercompass.projectresponsibility.repository;

import java.util.List;
import java.util.List;
import java.util.UUID;
import com.careercompass.projectresponsibility.domain.UserProfileProjectResponsibility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileProjectResponsibilityRepository
        extends JpaRepository<UserProfileProjectResponsibility, UUID> {

    List<UserProfileProjectResponsibility>
    findAllByUserProfileVersion_IdOrderByDisplayOrderAsc(UUID userProfileVersionId);
}
