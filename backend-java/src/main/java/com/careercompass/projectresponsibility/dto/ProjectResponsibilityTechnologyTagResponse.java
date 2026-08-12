package com.careercompass.projectresponsibility.dto;

import java.util.UUID;

public record ProjectResponsibilityTechnologyTagResponse(
        UUID technologyTagId,
        String canonicalName
) {
}
