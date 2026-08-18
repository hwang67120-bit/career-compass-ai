package com.careercompass.projectresponsibility.service;

public record ProjectResponsibilityExtractionOutcome(
        boolean requiresUserConfirmation,
        boolean partiallyExtracted
) {
}
