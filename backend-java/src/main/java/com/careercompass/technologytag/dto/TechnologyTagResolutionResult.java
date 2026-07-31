package com.careercompass.technologytag.dto;

import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTagMatchMethod;
import com.careercompass.technologytag.domain.TechnologyTagMatchStatus;

public record TechnologyTagResolutionResult(
        String rawName,
        UUID technologyTagId,
        String canonicalName,
        TechnologyTagMatchStatus matchStatus,
        TechnologyTagMatchMethod matchMethod
) {
}
