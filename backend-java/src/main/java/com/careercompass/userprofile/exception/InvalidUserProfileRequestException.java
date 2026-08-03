package com.careercompass.userprofile.exception;

public class InvalidUserProfileRequestException extends RuntimeException {

    private final String fieldName;
    private final String fieldMessage;

    public InvalidUserProfileRequestException(String fieldName, String fieldMessage) {
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
