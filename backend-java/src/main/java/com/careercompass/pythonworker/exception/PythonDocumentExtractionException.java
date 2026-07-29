package com.careercompass.pythonworker.exception;

import com.careercompass.pythonworker.dto.DocumentExtractionErrorResponse;
import org.springframework.http.HttpStatusCode;

public class PythonDocumentExtractionException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final DocumentExtractionErrorResponse response;

    public PythonDocumentExtractionException(
            HttpStatusCode statusCode,
            DocumentExtractionErrorResponse response
    ) {
        super(response.error().errorType());
        this.statusCode = statusCode;
        this.response = response;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public DocumentExtractionErrorResponse getResponse() {
        return response;
    }
}
