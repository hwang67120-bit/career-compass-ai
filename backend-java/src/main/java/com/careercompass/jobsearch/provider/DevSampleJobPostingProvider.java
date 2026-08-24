package com.careercompass.jobsearch.provider;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(prefix = "job-search", name = "provider", havingValue = "dev-sample")
public class DevSampleJobPostingProvider implements JobPostingProvider {

    private static final String PROVIDER_NAME = "DEV_SAMPLE";
    private static final String FIXTURE_RESOURCE =
            "fixtures/synthetic_job_postings_v1.json";
    private static final String SOURCE_URL_PREFIX =
            "https://example.invalid/synthetic-job-postings/";
    private static final ObjectMapper FIXTURE_OBJECT_MAPPER =
            new ObjectMapper();

    @Override
    public List<JobPostingCandidate> search(String keyword, int display) {
        if (keyword == null || display <= 0) {
            return List.of();
        }
        String targetJobTitle = normalizeForComparison(keyword);
        return loadFixtureCatalog().postings().stream()
                .filter(posting -> normalizeForComparison(posting.title())
                        .equals(targetJobTitle))
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

    private String normalizeForComparison(String jobTitle) {
        return Normalizer.normalize(jobTitle, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
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
            return FIXTURE_OBJECT_MAPPER.readValue(
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
