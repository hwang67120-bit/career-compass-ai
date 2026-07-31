package com.careercompass.technologytag.config;

import com.careercompass.technologytag.exception.InvalidTechnologyTagQueryException;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "technology-tag.policy")
public record TechnologyTagPolicyProperties(
        @Min(1) int maxQueryLength,
        @Min(1) int maxSearchResults
) {

    /**
     * 기능: 기술 태그 검색어가 설정된 최대 글자 수를 넘지 않는지 검증한다.
     */
    public void validateQuery(String query) {
        if (query.codePointCount(0, query.length()) > maxQueryLength) {
            throw new InvalidTechnologyTagQueryException();
        }
    }
}
