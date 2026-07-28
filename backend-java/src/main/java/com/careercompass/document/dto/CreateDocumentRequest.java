package com.careercompass.document.dto;

import com.careercompass.document.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentRequest(
        @NotNull(message = "문서 종류를 선택해 주세요.") DocumentType documentType,
        @NotBlank(message = "내용을 입력해 주세요.") String text
) {
}