package com.careercompass.jobsearch.exception;

public class Work24AccessException extends RuntimeException {

    private final Work24AccessFailure failure;

    public Work24AccessException(Work24AccessFailure failure) {
        this.failure = failure;
    }

    public Work24AccessException(Work24AccessFailure failure, Throwable cause) {
        super(cause);
        this.failure = failure;
    }

    public Work24AccessFailure getFailure() {
        return failure;
    }
}
