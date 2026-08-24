package com.careercompass.jobsearch.provider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(prefix = "job-search", name = "provider", havingValue = "dev-sample")
public class DevSampleJobPostingProvider implements JobPostingProvider {

    private static final String PROVIDER_NAME = "DEV_SAMPLE";
    private static final String FIXTURE_RESOURCE =
            "fixtures/synthetic_job_postings_v1.json";
    private static final String SOURCE_URL_PREFIX =
            "https://example.invalid/synthetic-job-postings/";

    private final ObjectMapper objectMapper;

    @Override
    public List<JobPostingCandidate> search(String keyword, int display) {
        if (keyword == null || display <= 0) {
            return List.of();
        }
        String targetJobTitle = keyword.strip();
        return loadFixtureCatalog().postings().stream()
                .filter(posting -> posting.title().equals(targetJobTitle))
                .limit(display)
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public String fetchSourceText(JobPostingCandidate candidate) {
        if (candidate == null || candidate.providerPostingId() == null) {
            throw new IllegalArgumentException("SYNTHETIC_JOB_POSTING_REQUIRED");
        }
        return loadFixtureCatalog().postings().stream()
                .filter(posting -> posting.fixtureId()
                        .equals(candidate.providerPostingId()))
                .findFirst()
                .map(SyntheticJobPostingFixture::sourceText)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SYNTHETIC_JOB_POSTING_NOT_FOUND"
                ));
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private JobPostingCandidate toCandidate(
            SyntheticJobPostingFixture posting
    ) {
        return new JobPostingCandidate(
                posting.fixtureId(),
                posting.companyName(),
                posting.title(),
                posting.location(),
                SOURCE_URL_PREFIX + posting.fixtureId(),
                null
        );
    }

    private SyntheticJobPostingFixtureCatalog loadFixtureCatalog() {
        ClassPathResource resource = new ClassPathResource(FIXTURE_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    SyntheticJobPostingFixtureCatalog.class
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "SYNTHETIC_JOB_POSTING_FIXTURE_INVALID",
                    exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SyntheticJobPostingFixtureCatalog(
            List<SyntheticJobPostingFixture> postings
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SyntheticJobPostingFixture(
            String fixtureId,
            String companyName,
            String title,
            String location,
            String sourceText
    ) {
    }
}
