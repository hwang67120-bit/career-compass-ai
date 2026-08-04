package com.careercompass.pythonworker.exception;

public class PythonExtractionException extends RuntimeException {

    private final PythonExtractionFailure failure;

    public PythonExtractionException(PythonExtractionFailure failure) {
        this.failure = failure;
    }

    public PythonExtractionException(PythonExtractionFailure failure, Throwable cause) {
        super(cause);
        this.failure = failure;
    }

    public PythonExtractionFailure getFailure() {
        return failure;
    }
}
