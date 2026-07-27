package com.careercompass.common.web;

import java.util.List;

import com.careercompass.document.exception.DocumentTextTooLargeException;
import com.careercompass.security.currentuser.CurrentUserUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiResponseFactory responseFactory;

    public GlobalExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return failure(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력 내용을 확인해 주세요.", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable() {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "요청 형식이나 문서 종류를 확인해 주세요.", List.of());
    }

    @ExceptionHandler(DocumentTextTooLargeException.class)
    ResponseEntity<ApiResponse<Void>> handleDocumentTextTooLarge() {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "문서 내용이 허용된 크기를 초과했습니다.",
                List.of(new FieldErrorDetail("text", "허용된 문서 크기를 초과했습니다.")));
    }

    @ExceptionHandler(CurrentUserUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> handleCurrentUserUnavailable() {
        return failure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.", List.of());
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String errorType,
                                                       String message, List<FieldErrorDetail> fieldErrors) {
        ApiError error = new ApiError(errorType, message, fieldErrors, false);
        return ResponseEntity.status(status).body(responseFactory.failure(error));
    }
}