package com.careercompass.jobanalysis.service;

import java.util.UUID;

public record ConfirmedProjectResponsibility(
        UUID evidenceId,
        UUID projectSourceId,
        String text
) {
}
