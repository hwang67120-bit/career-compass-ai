package com.careercompass.pythonworker.exception;

public class PythonEvidenceSimilarityException extends RuntimeException {

    private final PythonEvidenceSimilarityFailure failure;
    private final PythonEvidenceSimilarityResponseViolation responseViolation;

    public PythonEvidenceSimilarityException(PythonEvidenceSimilarityFailure failure) {
        this(failure, null, null);
    }

    public PythonEvidenceSimilarityException(
            PythonEvidenceSimilarityFailure failure,
            Throwable cause
    ) {
        this(failure, null, cause);
    }

    public PythonEvidenceSimilarityException(
            PythonEvidenceSimilarityFailure failure,
            PythonEvidenceSimilarityResponseViolation responseViolation
    ) {
        this(failure, responseViolation, null);
    }

    private PythonEvidenceSimilarityException(
            PythonEvidenceSimilarityFailure failure,
            PythonEvidenceSimilarityResponseViolation responseViolation,
            Throwable cause
    ) {
        super(failure.name()
                + (responseViolation == null ? "" : ": " + responseViolation.name()), cause);
        this.failure = failure;
        this.responseViolation = responseViolation;
    }

    public PythonEvidenceSimilarityFailure getFailure() {
        return failure;
    }

    public PythonEvidenceSimilarityResponseViolation getResponseViolation() {
        return responseViolation;
    }

    public boolean isRetryable() {
        return failure.isRetryable();
    }
}
