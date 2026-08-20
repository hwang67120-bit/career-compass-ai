package com.careercompass.jobsearch.provider;

import java.util.List;

import com.careercompass.jobsearch.client.PublicEmploymentJobSearchClient;
import com.careercompass.jobsearch.domain.JobPostingCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "job-search",
        name = "provider",
        havingValue = "public-employment"
)
public class PublicEmploymentJobPostingProvider implements JobPostingProvider {

    private static final String PROVIDER_NAME = "PUBLIC_EMPLOYMENT";

    private final PublicEmploymentJobSearchClient publicEmploymentJobSearchClient;

    public PublicEmploymentJobPostingProvider(
            PublicEmploymentJobSearchClient publicEmploymentJobSearchClient
    ) {
        this.publicEmploymentJobSearchClient = publicEmploymentJobSearchClient;
    }

    @Override
    public List<JobPostingCandidate> search(String keyword, int display) {
        return publicEmploymentJobSearchClient.search(keyword, display);
    }

    @Override
    public String fetchSourceText(JobPostingCandidate candidate) {
        return publicEmploymentJobSearchClient.fetchSourceText(candidate.providerPostingId());
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
