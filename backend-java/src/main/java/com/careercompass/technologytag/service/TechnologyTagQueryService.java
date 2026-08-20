package com.careercompass.technologytag.service;

import java.util.Comparator;

import com.careercompass.technologytag.config.TechnologyTagPolicyProperties;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.domain.TechnologyTagAlias;
import com.careercompass.technologytag.dto.TechnologyTagResponse;
import com.careercompass.technologytag.dto.TechnologyTagSearchResponse;
import com.careercompass.technologytag.normalization.TechnologyTagNameNormalizer;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TechnologyTagQueryService {

    private final TechnologyTagRepository repository;
    private final TechnologyTagNameNormalizer normalizer;
    private final TechnologyTagPolicyProperties policyProperties;

    /**
     * 기능: 표준 기술 태그의 이름, key와 별칭을 정규화된 검색어로 조회한다.
     * 반환 값: 기본 표시 순서로 정렬된 기술 태그와 일치한 별칭을 반환한다.
     */
    @Transactional(readOnly = true)
    public TechnologyTagSearchResponse searchTechnologyTags(String query) {
        String safeQuery = query == null ? "" : query;
        policyProperties.validateQuery(safeQuery);
        String normalizedQuery = normalizer.normalize(safeQuery);
        return new TechnologyTagSearchResponse(
                repository.searchActiveTechnologyTags(
                                normalizedQuery,
                                PageRequest.of(0, policyProperties.maxSearchResults())
                        )
                        .stream()
                        .map(technologyTag -> toResponse(technologyTag, normalizedQuery))
                        .toList()
        );
    }

    private TechnologyTagResponse toResponse(
            TechnologyTag technologyTag,
            String normalizedQuery
    ) {
        return new TechnologyTagResponse(
                technologyTag.getId(),
                technologyTag.getTagKey(),
                technologyTag.getDisplayName(),
                technologyTag.getCategory(),
                findMatchedAlias(technologyTag, normalizedQuery)
        );
    }

    private String findMatchedAlias(
            TechnologyTag technologyTag,
            String normalizedQuery
    ) {
        if (normalizedQuery.isBlank()
                || technologyTag.getNormalizedKey().contains(normalizedQuery)) {
            return null;
        }
        return technologyTag.getAliases()
                .stream()
                .filter(alias -> alias.getNormalizedAlias().contains(normalizedQuery))
                .min(Comparator.comparing(TechnologyTagAlias::getAlias))
                .map(TechnologyTagAlias::getAlias)
                .orElse(null);
    }
}
