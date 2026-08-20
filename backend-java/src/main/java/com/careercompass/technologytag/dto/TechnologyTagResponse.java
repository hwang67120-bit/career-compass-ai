package com.careercompass.technologytag.dto;

import java.util.UUID;

import com.careercompass.technologytag.domain.TechnologyTagCategory;

public record TechnologyTagResponse(
        UUID technologyTagId,
        String key,
        String displayName,
        TechnologyTagCategory category,
        String matchedAlias
) {
}
