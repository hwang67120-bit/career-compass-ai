package com.careercompass.technologytag.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TechnologyTagNameNormalizerTest {

    private final TechnologyTagNameNormalizer normalizer =
            new TechnologyTagNameNormalizer();

    @ParameterizedTest
    @CsvSource({
            "' Spring Boot ', springboot",
            "spring-boot, springboot",
            "SPRING_BOOT, springboot",
            "Ｃ＋＋, c++",
            "C#, c#"
    })
    void normalize_withCaseSpacingAndCompatibilityCharacters_returnsSearchKey(
            String sourceName,
            String expectedName
    ) {
        assertThat(normalizer.normalize(sourceName)).isEqualTo(expectedName);
    }
}
