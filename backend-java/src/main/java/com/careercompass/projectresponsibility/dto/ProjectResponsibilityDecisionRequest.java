package com.careercompass.projectresponsibility.dto;

public record ProjectResponsibilityDecisionRequest(
        long expectedVersion,
        Decision decision,
        String confirmedText
) {
    public enum Decision { CONFIRM, REJECT }
}
