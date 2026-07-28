package com.careercompass.document.controller;

import java.net.URI;

import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.document.dto.CreateDocumentRequest;
import com.careercompass.document.dto.CreateDocumentResponse;
import com.careercompass.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final ApiResponseFactory responseFactory;

    public DocumentController(DocumentService documentService, ApiResponseFactory responseFactory) {
        this.documentService = documentService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateDocumentResponse>> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        CreateDocumentResponse response = documentService.createDocument(request);
        return ResponseEntity
                .created(URI.create("/api/v1/documents/" + response.documentId()))
                .body(responseFactory.success(response));
    }
}