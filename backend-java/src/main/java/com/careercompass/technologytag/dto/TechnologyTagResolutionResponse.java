package com.careercompass.technologytag.dto;

import java.util.List;

public record TechnologyTagResolutionResponse(
        List<TechnologyTagResolutionResult> results
) {
}
