package com.careercompass.jobsearch.exception;

public class PublicEmploymentAccessException extends RuntimeException {

    private final PublicEmploymentAccessFailure failure;

    public PublicEmploymentAccessException(PublicEmploymentAccessFailure failure) {
        this.failure = failure;
    }

    public PublicEmploymentAccessException(
            PublicEmploymentAccessFailure failure,
            Throwable cause
    ) {
        super(cause);
        this.failure = failure;
    }

    public PublicEmploymentAccessFailure getFailure() {
        return failure;
    }
}
