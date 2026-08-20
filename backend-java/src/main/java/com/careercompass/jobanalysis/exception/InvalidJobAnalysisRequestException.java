package com.careercompass.jobanalysis.exception;

public class InvalidJobAnalysisRequestException extends RuntimeException {

    private final String fieldName;
    private final String fieldMessage;

    public InvalidJobAnalysisRequestException(String fieldName, String fieldMessage) {
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
