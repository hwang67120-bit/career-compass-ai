package com.careercompass.pythonworker.exception;

public class PythonExtractionException extends RuntimeException {

    private final PythonExtractionFailure failure;
    private final PythonExtractionResponseViolation responseViolation;

    public PythonExtractionException(PythonExtractionFailure failure) {
        this.failure = failure;
        this.responseViolation = null;
    }

    public PythonExtractionException(PythonExtractionFailure failure, String message) {
        super(message);
        this.failure = failure;
        this.responseViolation = null;
    }

    public PythonExtractionException(PythonExtractionFailure failure, Throwable cause) {
        super(cause);
        this.failure = failure;
        this.responseViolation = null;
    }

    public PythonExtractionException(
            PythonExtractionFailure failure,
            PythonExtractionResponseViolation responseViolation
    ) {
        super(failure + ": " + responseViolation);
        this.failure = failure;
        this.responseViolation = responseViolation;
    }

    public PythonExtractionFailure getFailure() {
        return failure;
    }

    public PythonExtractionResponseViolation getResponseViolation() {
        return responseViolation;
    }
}
