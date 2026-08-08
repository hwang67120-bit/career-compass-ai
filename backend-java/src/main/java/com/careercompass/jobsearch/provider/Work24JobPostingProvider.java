package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.client.Work24JobDetailFetcher;
import com.careercompass.jobsearch.client.Work24JobSearchClient;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link JobPostingProvider} 경계 뒤에서 고용24 공식 API를 호출하는 기본 구현이다.
 * `job-search.provider`가 명시적으로 다른 값(예: dev-sample)이 아니면 이 구현이
 * 선택된다(운영 기본값).
 */
@Component
@ConditionalOnProperty(
        prefix = "job-search",
        name = "provider",
        havingValue = "work24",
        matchIfMissing = true
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
