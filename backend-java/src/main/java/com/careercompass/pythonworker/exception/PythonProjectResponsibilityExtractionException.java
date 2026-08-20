package com.careercompass.pythonworker.exception;

public class PythonProjectResponsibilityExtractionException extends RuntimeException {

    private final PythonProjectResponsibilityExtractionFailure failure;
    private final PythonProjectResponsibilityExtractionResponseViolation responseViolation;

    public PythonProjectResponsibilityExtractionException(
            PythonProjectResponsibilityExtractionFailure failure
    ) {
        this(failure, null, null);
    }

    public PythonProjectResponsibilityExtractionException(
            PythonProjectResponsibilityExtractionFailure failure,
            Throwable cause
    ) {
        this(failure, null, cause);
    }

    public PythonProjectResponsibilityExtractionException(
            PythonProjectResponsibilityExtractionFailure failure,
            PythonProjectResponsibilityExtractionResponseViolation responseViolation
    ) {
        this(failure, responseViolation, null);
    }

    public PythonProjectResponsibilityExtractionException(
            PythonProjectResponsibilityExtractionFailure failure,
            PythonProjectResponsibilityExtractionResponseViolation responseViolation,
            Throwable cause
    ) {
        super(failure.name()
                + (responseViolation == null ? "" : ": " + responseViolation.name()), cause);
        this.failure = failure;
        this.responseViolation = responseViolation;
    }

    public PythonProjectResponsibilityExtractionFailure getFailure() {
        return failure;
    }

    public PythonProjectResponsibilityExtractionResponseViolation getResponseViolation() {
        return responseViolation;
    }

    public boolean isRetryable() {
        return failure.isRetryable();
    }
}
