package com.careercompass.technologytag.dto;

import java.util.List;

public record TechnologyTagSearchResponse(
        List<TechnologyTagResponse> technologyTags
) {
}
