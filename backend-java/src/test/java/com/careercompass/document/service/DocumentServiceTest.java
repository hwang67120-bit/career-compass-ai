package com.careercompass.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.careercompass.document.config.DocumentPolicyProperties;
import com.careercompass.document.domain.DocumentStatus;
import com.careercompass.document.domain.DocumentType;
import com.careercompass.document.domain.UserDocument;
import com.careercompass.document.dto.CreateDocumentRequest;
import com.careercompass.document.dto.CreateDocumentResponse;
import com.careercompass.document.exception.DocumentTextTooLargeException;
import com.careercompass.document.privacy.BasicPersonalInformationSanitizer;
import com.careercompass.document.repository.UserDocumentRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DocumentServiceTest {

    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-25T01:00:00Z");

    private UserDocumentRepository repository;
    private CurrentUserProvider currentUserProvider;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserDocumentRepository.class);
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(repository.save(Mockito.any(UserDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        documentService = new DocumentService(repository, currentUserProvider,
                new DocumentPolicyProperties(1000), new BasicPersonalInformationSanitizer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createDocument_withValidText_savesCurrentUsersDocument() {
        CreateDocumentRequest request = new CreateDocumentRequest(
                DocumentType.RESUME, "연락처 user@example.com, Java 프로젝트 경험");

        CreateDocumentResponse response = documentService.createDocument(request);

        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(repository).save(captor.capture());
        UserDocument savedDocument = captor.getValue();
        assertThat(savedDocument.getUserId()).isEqualTo(USER_ID);
        assertThat(savedDocument.getOriginalText()).contains("user@example.com");
        assertThat(savedDocument.getAnalysisText()).doesNotContain("user@example.com").contains("[EMAIL]");
        assertThat(savedDocument.getDocumentStatus()).isEqualTo(DocumentStatus.REGISTERED);
        assertThat(savedDocument.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.documentId()).isEqualTo(savedDocument.getId());
        assertThat(response.documentStatus()).isEqualTo(DocumentStatus.REGISTERED);
    }

    @Test
    void createDocument_withOversizedText_rejectsBeforeSaving() {
        DocumentService limitedService = new DocumentService(repository, currentUserProvider,
                new DocumentPolicyProperties(5), new BasicPersonalInformationSanitizer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        CreateDocumentRequest request = new CreateDocumentRequest(DocumentType.PORTFOLIO, "123456");

        assertThatThrownBy(() -> limitedService.createDocument(request))
                .isInstanceOf(DocumentTextTooLargeException.class);
        verifyNoInteractions(repository);
    }
}