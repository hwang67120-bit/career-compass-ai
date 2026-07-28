package com.careercompass.common.web;

import java.util.List;

import com.careercompass.document.exception.DocumentTextTooLargeException;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
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

    @ExceptionHandler(InvalidGitHubRepositoryUrlException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidGitHubRepositoryUrl() {
        return failure(
                HttpStatus.BAD_REQUEST,
                "INVALID_GITHUB_REPOSITORY_URL",
                "올바른 공개 GitHub 저장소 주소를 입력해 주세요.",
                List.of(new FieldErrorDetail(
                        "repositoryUrl",
                        "https://github.com/{소유자}/{저장소} 형식이어야 합니다."
                ))
        );
    }

    @ExceptionHandler(GitHubAccessException.class)
    ResponseEntity<ApiResponse<Void>> handleGitHubAccess(GitHubAccessException exception) {
        GitHubAccessFailure failure = exception.getFailure();
        return switch (failure) {
            case REPOSITORY_UNAVAILABLE -> failure(
                    HttpStatus.NOT_FOUND,
                    "GITHUB_REPOSITORY_UNAVAILABLE",
                    "공개 GitHub 저장소를 확인할 수 없습니다.",
                    List.of(),
                    false
            );
            case RATE_LIMITED -> failure(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "GITHUB_RATE_LIMITED",
                    "GitHub 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
                    List.of(),
                    true
            );
            case REDIRECTED, SERVICE_UNAVAILABLE, INVALID_RESPONSE -> failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GITHUB_SERVICE_UNAVAILABLE",
                    "GitHub 저장소를 확인하는 중 문제가 발생했습니다.",
                    List.of(),
                    failure != GitHubAccessFailure.REDIRECTED
            );
        };
    }

    @ExceptionHandler(CurrentUserUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> handleCurrentUserUnavailable() {
        return failure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.", List.of());
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String errorType,
                                                       String message, List<FieldErrorDetail> fieldErrors) {
        return failure(status, errorType, message, fieldErrors, false);
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status, String errorType, String message,
            List<FieldErrorDetail> fieldErrors, boolean retryable
    ) {
        ApiError error = new ApiError(errorType, message, fieldErrors, retryable);
        return ResponseEntity.status(status).body(responseFactory.failure(error));
    }
}
