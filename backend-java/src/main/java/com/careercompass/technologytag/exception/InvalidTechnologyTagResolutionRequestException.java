package com.careercompass.technologytag.exception;

public class InvalidTechnologyTagResolutionRequestException extends RuntimeException {

    private final String fieldName;
    private final String fieldMessage;

    public InvalidTechnologyTagResolutionRequestException(
            String fieldName,
            String fieldMessage
    ) {
        this.fieldName = fieldName;
        this.fieldMessage = fieldMessage;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldMessage() {
        return fieldMessage;
    }
}
