package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.client.Work24JobDetailFetcher;
import com.careercompass.jobsearch.client.Work24JobSearchClient;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link JobPostingProvider} 경계 뒤에서 고용24 공식 API를 호출하는 구현이다.
 * `job-search.provider=work24`를 명시적으로 설정해야만 선택된다 — 기본으로 선택되지
 * 않는다. 개인 회원 계정으로는 실제 호출이 거부됨을 확인했고(2026-08-05), 상세 페이지
 * 실제 DOM 표본·담당자 정보 제거가 검증되기 전까지는 기본 Provider로 쓰지 않는다
 * (코덱스 확인, PR #48).
 */
@Component
@ConditionalOnProperty(
        prefix = "job-search",
        name = "provider",
        havingValue = "work24"
)
public class Work24JobPostingProvider implements JobPostingProvider {

    private static final String PROVIDER_NAME = "WORK24";

    private final Work24JobSearchClient work24JobSearchClient;
    private final Work24JobDetailFetcher work24JobDetailFetcher;

    public Work24JobPostingProvider(
            Work24JobSearchClient work24JobSearchClient,
            Work24JobDetailFetcher work24JobDetailFetcher
    ) {
        this.work24JobSearchClient = work24JobSearchClient;
        this.work24JobDetailFetcher = work24JobDetailFetcher;
    }

    @Override
    public List<JobPostingCandidate> search(String keyword, int display) {
        return work24JobSearchClient.search(keyword, display);
    }

    @Override
    public String fetchSourceText(JobPostingCandidate candidate) {
        return work24JobDetailFetcher.fetchSourceText(candidate.providerPostingId());
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
