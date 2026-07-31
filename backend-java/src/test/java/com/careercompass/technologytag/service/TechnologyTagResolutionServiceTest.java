package com.careercompass.technologytag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.careercompass.technologytag.config.TechnologyTagResolutionPolicyProperties;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.domain.TechnologyTagAlias;
import com.careercompass.technologytag.domain.TechnologyTagMatchMethod;
import com.careercompass.technologytag.domain.TechnologyTagMatchStatus;
import com.careercompass.technologytag.dto.TechnologyTagResolutionResponse;
import com.careercompass.technologytag.exception.InvalidTechnologyTagResolutionRequestException;
import com.careercompass.technologytag.normalization.TechnologyTagNameNormalizer;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TechnologyTagResolutionServiceTest {

    private static final UUID JAVA_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID JAVASCRIPT_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000003");

    private TechnologyTagRepository repository;
    private TechnologyTagResolutionService service;

    @BeforeEach
    void setUp() {
        repository = mock(TechnologyTagRepository.class);
        service = new TechnologyTagResolutionService(
                repository,
                new TechnologyTagNameNormalizer(),
                new TechnologyTagResolutionPolicyProperties(30, 100)
        );
    }

    @Test
    void resolveTechnologyNames_withCanonicalAliasAndUnknown_preservesInputResults() {
        TechnologyTag java = technologyTag(
                JAVA_ID,
                "java",
                "Java",
                Set.of()
        );
        TechnologyTagAlias javascriptAlias = alias("js");
        TechnologyTag javascript = technologyTag(
                JAVASCRIPT_ID,
                "javascript",
                "JavaScript",
                Set.of(javascriptAlias)
        );
        when(repository.findActiveResolutionMatches(anySet()))
                .thenReturn(List.of(java, javascript));

        TechnologyTagResolutionResponse response =
                service.resolveTechnologyNames(
                        List.of("Java", " java ", "JS", "unknown-tool")
                );

        assertThat(response.results()).hasSize(4);
        assertThat(response.results().get(0).rawName()).isEqualTo("Java");
        assertThat(response.results().get(0).technologyTagId()).isEqualTo(JAVA_ID);
        assertThat(response.results().get(0).matchMethod())
                .isEqualTo(TechnologyTagMatchMethod.CANONICAL);
        assertThat(response.results().get(1).technologyTagId()).isEqualTo(JAVA_ID);
        assertThat(response.results().get(2).technologyTagId()).isEqualTo(JAVASCRIPT_ID);
        assertThat(response.results().get(2).matchMethod())
                .isEqualTo(TechnologyTagMatchMethod.ALIAS);
        assertThat(response.results().get(3).matchStatus())
                .isEqualTo(TechnologyTagMatchStatus.UNRESOLVED);
        assertThat(response.results().get(3).matchMethod())
                .isEqualTo(TechnologyTagMatchMethod.NONE);
    }

    @Test
    void resolveTechnologyNames_withTooManyNames_rejectsRequestBeforeRepositoryCall() {
        List<String> technologyNames =
                Collections.nCopies(31, "Java");

        assertThatThrownBy(() ->
                service.resolveTechnologyNames(technologyNames))
                .isInstanceOf(InvalidTechnologyTagResolutionRequestException.class)
                .extracting(exception ->
                        ((InvalidTechnologyTagResolutionRequestException) exception)
                                .getFieldName())
                .isEqualTo("technologyNames");
    }

    @Test
    void resolveTechnologyNames_withBlankOrTooLongName_reportsInputIndex() {
        assertThatThrownBy(() ->
                service.resolveTechnologyNames(List.of("Java", " ")))
                .isInstanceOf(InvalidTechnologyTagResolutionRequestException.class)
                .extracting(exception ->
                        ((InvalidTechnologyTagResolutionRequestException) exception)
                                .getFieldName())
                .isEqualTo("technologyNames[1]");

        assertThatThrownBy(() ->
                service.resolveTechnologyNames(List.of("가".repeat(101))))
                .isInstanceOf(InvalidTechnologyTagResolutionRequestException.class)
                .extracting(exception ->
                        ((InvalidTechnologyTagResolutionRequestException) exception)
                                .getFieldName())
                .isEqualTo("technologyNames[0]");
    }

    private TechnologyTag technologyTag(
            UUID id,
            String normalizedKey,
            String displayName,
            Set<TechnologyTagAlias> aliases
    ) {
        TechnologyTag technologyTag = mock(TechnologyTag.class);
        when(technologyTag.getId()).thenReturn(id);
        when(technologyTag.getNormalizedKey()).thenReturn(normalizedKey);
        when(technologyTag.getDisplayName()).thenReturn(displayName);
        when(technologyTag.getAliases()).thenReturn(aliases);
        return technologyTag;
    }

    private TechnologyTagAlias alias(String normalizedAlias) {
        TechnologyTagAlias alias = mock(TechnologyTagAlias.class);
        when(alias.getNormalizedAlias()).thenReturn(normalizedAlias);
        return alias;
    }
}
