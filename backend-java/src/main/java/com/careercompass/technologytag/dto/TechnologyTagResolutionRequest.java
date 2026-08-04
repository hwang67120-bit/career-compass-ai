package com.careercompass.technologytag.dto;

import java.util.List;

public record TechnologyTagResolutionRequest(
        List<String> technologyNames
) {
}
