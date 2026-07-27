package com.careercompass.document.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_document")
public class UserDocument {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "original_text", nullable = false, columnDefinition = "text")
    private String originalText;

    @Column(name = "analysis_text", nullable = false, columnDefinition = "text")
    private String analysisText;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 30)
    private DocumentStatus documentStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserDocument() {
    }

    private UserDocument(UUID id, UUID userId, DocumentType documentType, String originalText,
                         String analysisText, DocumentStatus documentStatus, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.documentType = documentType;
        this.originalText = originalText;
        this.analysisText = analysisText;
        this.documentStatus = documentStatus;
        this.createdAt = createdAt;
    }

    public static UserDocument create(UUID id, UUID userId, DocumentType documentType,
                                      String originalText, String analysisText, Instant createdAt) {
        return new UserDocument(id, userId, documentType, originalText, analysisText,
                DocumentStatus.REGISTERED, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getOriginalText() { return originalText; }
    public String getAnalysisText() { return analysisText; }
    public DocumentStatus getDocumentStatus() { return documentStatus; }
    public Instant getCreatedAt() { return createdAt; }
}