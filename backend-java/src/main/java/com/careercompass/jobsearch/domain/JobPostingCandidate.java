package com.careercompass.jobsearch.domain;

/**
 * 활성 채용공고 Provider에서 검색·조회한 공고 한 건을 Python 추출 이전까지 정규화한 값이다.
 * sourceText는 상세 조회에서 별도로 채워지며, 목록 조회 시점에는 null이다.
 */
public record JobPostingCandidate(
        String providerPostingId,
        String companyName,
        String originalJobTitle,
        String locationText,
        String sourceUrl,
        String sourceText
) {

    public JobPostingCandidate withSourceText(String sourceText) {
        return new JobPostingCandidate(
                providerPostingId,
                companyName,
                originalJobTitle,
                locationText,
                sourceUrl,
                sourceText
        );
    }
}
