package com.careercompass.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.careercompass.document.domain.DocumentStatus;
import com.careercompass.document.domain.DocumentType;

public record CreateDocumentResponse(
        UUID documentId,
        DocumentType documentType,
        DocumentStatus documentStatus,
        Instant createdAt
) {
}