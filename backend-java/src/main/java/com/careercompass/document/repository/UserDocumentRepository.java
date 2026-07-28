package com.careercompass.document.repository;

import java.util.UUID;

import com.careercompass.document.domain.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
}