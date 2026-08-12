package com.careercompass.pythonworker.exception;

public enum PythonEvidenceSimilarityFailure {
    REQUEST_INVALID(false),
    RESPONSE_INVALID(false),
    MODEL_UNAVAILABLE(true);

    private final boolean retryable;

    PythonEvidenceSimilarityFailure(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
