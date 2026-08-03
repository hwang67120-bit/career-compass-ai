package com.careercompass.userprofile.controller;

import java.util.List;

import com.careercompass.common.web.ApiError;
import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.common.web.FieldErrorDetail;
import com.careercompass.userprofile.exception.InvalidUserProfileRequestException;
import com.careercompass.userprofile.exception.UserProfileNotFoundException;
import com.careercompass.userprofile.exception.UserProfileVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserProfileController.class)
public class UserProfileExceptionHandler {

    private final ApiResponseFactory responseFactory;

    public UserProfileExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(InvalidUserProfileRequestException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidUserProfileRequest(
            InvalidUserProfileRequestException exception
    ) {
        return failure(
                HttpStatus.BAD_REQUEST,
                "INVALID_USER_PROFILE",
                "사용자 분석 프로필 입력을 확인해 주세요.",
                List.of(new FieldErrorDetail(
                        exception.getFieldName(),
                        exception.getFieldMessage()
                ))
        );
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleUserProfileNotFound() {
        return failure(
                HttpStatus.NOT_FOUND,
                "USER_PROFILE_NOT_FOUND",
                "사용자 분석 프로필을 찾을 수 없습니다.",
                List.of()
        );
    }

    @ExceptionHandler(UserProfileVersionConflictException.class)
    ResponseEntity<ApiResponse<Void>> handleUserProfileVersionConflict() {
        return failure(
                HttpStatus.CONFLICT,
                "USER_PROFILE_VERSION_CONFLICT",
                "사용자 분석 프로필이 변경되었습니다. 최신 버전을 확인해 주세요.",
                List.of()
        );
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status,
            String errorType,
            String message,
            List<FieldErrorDetail> fieldErrors
    ) {
        ApiError error = new ApiError(
                errorType,
                message,
                fieldErrors,
                false
        );
        return ResponseEntity.status(status)
                .body(responseFactory.failure(error));
    }
}
