package com.careercompass.projectresponsibility.controller;

import com.careercompass.common.web.*;
import com.careercompass.projectresponsibility.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ProjectResponsibilityController.class)
public class ProjectResponsibilityExceptionHandler {
    private final ApiResponseFactory responseFactory;
    public ProjectResponsibilityExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }
    @ExceptionHandler(InvalidProjectResponsibilityDecisionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalid() {
        return responseFactory.failure(new ApiError(
                "INVALID_PROJECT_RESPONSIBILITY_DECISION", "후보 결정 요청이 올바르지 않습니다.", null, false));
    }
    @ExceptionHandler(ProjectResponsibilityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> notFound() {
        return responseFactory.failure(new ApiError(
                "PROJECT_RESPONSIBILITY_CANDIDATE_NOT_FOUND", "프로젝트 분석 후보를 찾을 수 없습니다.", null, false));
    }
    @ExceptionHandler(ProjectResponsibilityConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> conflict() {
        return responseFactory.failure(new ApiError(
                "PROJECT_RESPONSIBILITY_CANDIDATE_VERSION_CONFLICT", "후보 버전이 변경되었습니다.", null, false));
    }
    @ExceptionHandler(ProjectResponsibilityStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> stateConflict() {
        return responseFactory.failure(new ApiError(
                "PROJECT_RESPONSIBILITY_CANDIDATE_STATE_CONFLICT",
                "후보가 이미 다른 상태로 결정되었습니다.", null, false));
    }
    @ExceptionHandler(ProjectResponsibilityExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ApiResponse<Void> expired() {
        return responseFactory.failure(new ApiError(
                "PROJECT_RESPONSIBILITY_CANDIDATE_EXPIRED", "프로젝트 분석 후보가 만료되었습니다.", null, false));
    }
}
