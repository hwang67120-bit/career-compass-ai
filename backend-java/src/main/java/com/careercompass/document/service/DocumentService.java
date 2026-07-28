package com.careercompass.document.service;

import java.time.Clock;
import java.util.UUID;

import com.careercompass.document.config.DocumentPolicyProperties;
import com.careercompass.document.domain.UserDocument;
import com.careercompass.document.dto.CreateDocumentRequest;
import com.careercompass.document.dto.CreateDocumentResponse;
import com.careercompass.document.privacy.DocumentTextSanitizer;
import com.careercompass.document.repository.UserDocumentRepository;
import com.careercompass.security.currentuser.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final UserDocumentRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentPolicyProperties policy;
    private final DocumentTextSanitizer textSanitizer;
    private final Clock clock;

    public DocumentService(UserDocumentRepository repository,
                           CurrentUserProvider currentUserProvider,
                           DocumentPolicyProperties policy,
                           DocumentTextSanitizer textSanitizer,
                           Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.policy = policy;
        this.textSanitizer = textSanitizer;
        this.clock = clock;
    }

    /**
     * 기능: 현재 사용자의 원문과 개인정보를 가린 분석용 문서를 등록한다.
     * 반환 값: 생성된 문서 식별자, 종류, 상태와 생성 시각을 반환한다.
     */
    @Transactional
    public CreateDocumentResponse createDocument(CreateDocumentRequest request) {
        policy.validateText(request.text());
        UserDocument document = UserDocument.create(
                UUID.randomUUID(),
                currentUserProvider.getCurrentUserId(),
                request.documentType(),
                request.text(),
                textSanitizer.sanitize(request.text()),
                clock.instant()
        );
        UserDocument savedDocument = repository.save(document);
        return new CreateDocumentResponse(
                savedDocument.getId(),
                savedDocument.getDocumentType(),
                savedDocument.getDocumentStatus(),
                savedDocument.getCreatedAt()
        );
    }
}