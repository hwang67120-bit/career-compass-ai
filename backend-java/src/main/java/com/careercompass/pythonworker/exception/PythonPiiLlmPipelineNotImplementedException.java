package com.careercompass.pythonworker.exception;

import com.careercompass.pythonworker.dto.DocumentExtractionErrorResponse;
import org.springframework.http.HttpStatusCode;

public class PythonPiiLlmPipelineNotImplementedException extends PythonDocumentExtractionException {

    public PythonPiiLlmPipelineNotImplementedException(
            HttpStatusCode statusCode,
            DocumentExtractionErrorResponse response
    ) {
        super(statusCode, response);
    }
}
