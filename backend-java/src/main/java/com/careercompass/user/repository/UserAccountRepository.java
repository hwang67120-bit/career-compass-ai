package com.careercompass.user.repository;

import java.util.UUID;

import com.careercompass.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
}
