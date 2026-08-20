package com.careercompass.jobanalysis.controller;

import java.util.List;

import com.careercompass.common.web.ApiError;
import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.common.web.FieldErrorDetail;
import com.careercompass.jobanalysis.exception.InvalidJobAnalysisRequestException;
import com.careercompass.jobanalysis.exception.JobAnalysisInputNotFoundException;
import com.careercompass.jobanalysis.exception.JobAnalysisNotFoundException;
import com.careercompass.jobanalysis.exception.ProjectSourceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = JobAnalysisController.class)
public class JobAnalysisExceptionHandler {

    private final ApiResponseFactory responseFactory;

    public JobAnalysisExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(InvalidJobAnalysisRequestException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
            InvalidJobAnalysisRequestException exception
    ) {
        return failure(
                HttpStatus.BAD_REQUEST,
                "INVALID_JOB_ANALYSIS_REQUEST",
                "분석 시작 요청을 확인해 주세요.",
                List.of(new FieldErrorDetail(
                        exception.getFieldName(),
                        exception.getFieldMessage()
                ))
        );
    }

    @ExceptionHandler(JobAnalysisInputNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleInputNotFound() {
        return failure(
                HttpStatus.NOT_FOUND,
                "JOB_ANALYSIS_INPUT_NOT_FOUND",
                "분석에 사용할 프로필 또는 저장소를 찾을 수 없습니다.",
                List.of()
        );
    }

    @ExceptionHandler(JobAnalysisNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleJobAnalysisNotFound() {
        return failure(
                HttpStatus.NOT_FOUND,
                "JOB_ANALYSIS_NOT_FOUND",
                "분석 작업을 찾을 수 없습니다.",
                List.of()
        );
    }

    @ExceptionHandler(ProjectSourceUnavailableException.class)
    ResponseEntity<ApiResponse<Void>> handleProjectSourceUnavailable() {
        return failure(
                HttpStatus.CONFLICT,
                "PROJECT_SOURCE_UNAVAILABLE",
                "현재 분석에 사용할 수 없는 저장소가 포함되어 있습니다.",
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
