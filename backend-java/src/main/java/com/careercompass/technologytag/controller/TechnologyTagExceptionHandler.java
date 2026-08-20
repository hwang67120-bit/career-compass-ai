package com.careercompass.technologytag.controller;

import java.util.List;

import com.careercompass.common.web.ApiError;
import com.careercompass.common.web.ApiResponse;
import com.careercompass.common.web.ApiResponseFactory;
import com.careercompass.common.web.FieldErrorDetail;
import com.careercompass.technologytag.exception.InvalidTechnologyTagQueryException;
import com.careercompass.technologytag.exception.InvalidTechnologyTagResolutionRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        TechnologyTagController.class,
        InternalTechnologyTagResolutionController.class
})
public class TechnologyTagExceptionHandler {

    private final ApiResponseFactory responseFactory;

    public TechnologyTagExceptionHandler(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(InvalidTechnologyTagQueryException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidTechnologyTagQuery() {
        ApiError error = new ApiError(
                "INVALID_TECHNOLOGY_TAG_QUERY",
                "기술 태그 검색어가 허용된 길이를 초과했습니다.",
                List.of(new FieldErrorDetail(
                        "query",
                        "허용된 검색어 길이를 초과했습니다."
                )),
                false
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(responseFactory.failure(error));
    }

    @ExceptionHandler(InvalidTechnologyTagResolutionRequestException.class)
    ResponseEntity<ApiResponse<Void>> handleInvalidTechnologyTagResolutionRequest(
            InvalidTechnologyTagResolutionRequestException exception
    ) {
        ApiError error = new ApiError(
                "INVALID_TECHNOLOGY_TAG_RESOLUTION_REQUEST",
                "기술 태그 정규화 요청을 확인해 주세요.",
                List.of(new FieldErrorDetail(
                        exception.getFieldName(),
                        exception.getFieldMessage()
                )),
                false
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(responseFactory.failure(error));
    }
}
