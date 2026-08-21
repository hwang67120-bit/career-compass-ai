package com.careercompass.jobanalysis.service.model;

import java.util.UUID;

public record ConfirmedProjectResponsibilityEvidence(
        UUID evidenceId,
        UUID projectSourceId,
        String text
) {
}
