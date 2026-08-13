package com.careercompass.pythonworker.exception;

public enum PythonProjectResponsibilityExtractionFailure {
    REQUEST_INVALID(false),
    RESPONSE_INVALID(false),
    MODEL_UNAVAILABLE(true);

    private final boolean retryable;

    PythonProjectResponsibilityExtractionFailure(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
