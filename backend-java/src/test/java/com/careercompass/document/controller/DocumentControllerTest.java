package com.careercompass.document.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.common.web.GlobalExceptionHandler;
import com.careercompass.document.domain.DocumentStatus;
import com.careercompass.document.domain.DocumentType;
import com.careercompass.document.dto.CreateDocumentResponse;
import com.careercompass.document.service.DocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DocumentControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-25T01:00:00Z");
    private static final UUID DOCUMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = Mockito.mock(DocumentService.class);
        ApiResponseFactory responseFactory = new ApiResponseFactory(Clock.fixed(CREATED_AT, ZoneOffset.UTC));
        DocumentController controller = new DocumentController(documentService, responseFactory);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(responseFactory))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void closeValidator() {
        validator.close();
    }

    @Test
    void createDocument_withValidRequest_returnsCreatedDocument() throws Exception {
        when(documentService.createDocument(any())).thenReturn(new CreateDocumentResponse(
                DOCUMENT_ID, DocumentType.RESUME, DocumentStatus.REGISTERED, CREATED_AT));

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"RESUME","text":"Spring Boot 프로젝트 경험"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/" + DOCUMENT_ID))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.documentId").value(DOCUMENT_ID.toString()))
                .andExpect(jsonPath("$.data.documentType").value("RESUME"))
                .andExpect(jsonPath("$.data.documentStatus").value("REGISTERED"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void createDocument_withBlankText_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"RESUME","text":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors[0].fieldName").value("text"));
    }

    @Test
    void createDocument_withUnknownDocumentType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"UNKNOWN","text":"경력 내용"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errorType").value("INVALID_INPUT"));
    }
}