package com.careercompass.technologytag.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import com.careercompass.technologytag.config.TechnologyTagResolutionPolicyProperties;
import com.careercompass.technologytag.domain.TechnologyTag;
import com.careercompass.technologytag.domain.TechnologyTagAlias;
import com.careercompass.technologytag.domain.TechnologyTagMatchMethod;
import com.careercompass.technologytag.domain.TechnologyTagMatchStatus;
import com.careercompass.technologytag.dto.TechnologyTagResolutionResponse;
import com.careercompass.technologytag.dto.TechnologyTagResolutionResult;
import com.careercompass.technologytag.exception.InvalidTechnologyTagResolutionRequestException;
import com.careercompass.technologytag.normalization.TechnologyTagNameNormalizer;
import com.careercompass.technologytag.repository.TechnologyTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnologyTagResolutionService {

    private final TechnologyTagRepository repository;
    private final TechnologyTagNameNormalizer normalizer;
    private final TechnologyTagResolutionPolicyProperties policyProperties;

    public TechnologyTagResolutionService(
            TechnologyTagRepository repository,
            TechnologyTagNameNormalizer normalizer,
            TechnologyTagResolutionPolicyProperties policyProperties
    ) {
        this.repository = repository;
        this.normalizer = normalizer;
        this.policyProperties = policyProperties;
    }

    /**
     * 기능: 기술명 원문을 활성 표준 태그의 대표 이름 또는 확인된 별칭과 정확히 연결한다.
     * 반환 값: 입력 순서와 원문을 보존한 기술 태그 정규화 결과를 반환한다.
     */
    @Transactional(readOnly = true)
    public TechnologyTagResolutionResponse resolveTechnologyNames(
            List<String> technologyNames
    ) {
        validateTechnologyNames(technologyNames);
        List<String> normalizedNames = technologyNames.stream()
                .map(normalizer::normalize)
                .toList();
        Set<String> uniqueNormalizedNames =
                new LinkedHashSet<>(normalizedNames);
        List<TechnologyTag> matches =
                repository.findActiveResolutionMatches(uniqueNormalizedNames);

        Map<String, TechnologyTag> canonicalMatches = new HashMap<>();
        Map<String, TechnologyTag> aliasMatches = new HashMap<>();
        for (TechnologyTag technologyTag : matches) {
            canonicalMatches.put(
                    technologyTag.getNormalizedKey(),
                    technologyTag
            );
            for (TechnologyTagAlias alias : technologyTag.getAliases()) {
                aliasMatches.put(alias.getNormalizedAlias(), technologyTag);
            }
        }

        List<TechnologyTagResolutionResult> results =
                IntStream.range(0, technologyNames.size())
                        .mapToObj(index -> resolveTechnologyName(
                                technologyNames.get(index),
                                normalizedNames.get(index),
                                canonicalMatches,
                                aliasMatches
                        ))
                        .toList();
        return new TechnologyTagResolutionResponse(results);
    }

    private TechnologyTagResolutionResult resolveTechnologyName(
            String rawName,
            String normalizedName,
            Map<String, TechnologyTag> canonicalMatches,
            Map<String, TechnologyTag> aliasMatches
    ) {
        TechnologyTag canonicalMatch = canonicalMatches.get(normalizedName);
        if (canonicalMatch != null) {
            return matchedResult(
                    rawName,
                    canonicalMatch,
                    TechnologyTagMatchMethod.CANONICAL
            );
        }

        TechnologyTag aliasMatch = aliasMatches.get(normalizedName);
        if (aliasMatch != null) {
            return matchedResult(
                    rawName,
                    aliasMatch,
                    TechnologyTagMatchMethod.ALIAS
            );
        }

        return new TechnologyTagResolutionResult(
                rawName,
                null,
                null,
                TechnologyTagMatchStatus.UNRESOLVED,
                TechnologyTagMatchMethod.NONE
        );
    }

    private TechnologyTagResolutionResult matchedResult(
            String rawName,
            TechnologyTag technologyTag,
            TechnologyTagMatchMethod matchMethod
    ) {
        return new TechnologyTagResolutionResult(
                rawName,
                technologyTag.getId(),
                technologyTag.getDisplayName(),
                TechnologyTagMatchStatus.MATCHED,
                matchMethod
        );
    }

    private void validateTechnologyNames(List<String> technologyNames) {
        if (technologyNames == null || technologyNames.isEmpty()) {
            throw invalidRequest(
                    "technologyNames",
                    "기술명을 한 개 이상 입력해 주세요."
            );
        }
        if (technologyNames.size() > policyProperties.maxNames()) {
            throw invalidRequest(
                    "technologyNames",
                    "허용된 기술명 개수를 초과했습니다."
            );
        }
        for (int index = 0; index < technologyNames.size(); index++) {
            String technologyName = technologyNames.get(index);
            String fieldName = "technologyNames[" + index + "]";
            if (technologyName == null || technologyName.isBlank()) {
                throw invalidRequest(
                        fieldName,
                        "기술명은 비어 있을 수 없습니다."
                );
            }
            if (technologyName.codePointCount(0, technologyName.length())
                    > policyProperties.maxNameLength()) {
                throw invalidRequest(
                        fieldName,
                        "허용된 기술명 길이를 초과했습니다."
                );
            }
        }
    }

    private InvalidTechnologyTagResolutionRequestException invalidRequest(
            String fieldName,
            String fieldMessage
    ) {
        return new InvalidTechnologyTagResolutionRequestException(
                fieldName,
                fieldMessage
        );
    }
}
