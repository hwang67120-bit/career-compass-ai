package com.careercompass.jobsearch.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.careercompass.jobsearch.domain.JobPostingCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DevSampleJobPostingProviderTest {

    private final DevSampleJobPostingProvider provider =
            new DevSampleJobPostingProvider(new ObjectMapper());

    @Test
    void search_withExactBackendTitle_returnsBackendFixture() {
        List<JobPostingCandidate> postings =
                provider.search("  백엔드 개발자  ", 10);

        assertThat(postings).singleElement().satisfies(posting -> {
            assertThat(posting.providerPostingId())
                    .isEqualTo("synthetic-backend-001");
            assertThat(posting.companyName())
                    .isEqualTo("가상 공공디지털서비스원");
            assertThat(posting.originalJobTitle())
                    .isEqualTo("백엔드 개발자");
        });
    }

    @Test
    void search_withMachineLearningTitle_returnsDifferentFixture() {
        List<JobPostingCandidate> postings =
                provider.search("머신러닝 엔지니어", 10);

        assertThat(postings).singleElement()
                .extracting(JobPostingCandidate::providerPostingId)
                .isEqualTo("synthetic-ml-001");
    }

    @Test
    void search_withExcludedLlmEngineerTitle_returnsEmptyList() {
        assertThat(provider.search("LLM 엔지니어", 10)).isEmpty();
    }

    @Test
    void search_withNoExactTitleMatch_returnsEmptyList() {
        assertThat(provider.search("백엔드", 10)).isEmpty();
    }

    @Test
    void search_withZeroDisplay_returnsEmptyList() {
        assertThat(provider.search("백엔드 개발자", 0)).isEmpty();
    }

    @Test
    void fetchSourceText_withSelectedFixture_returnsFixtureBody() {
        JobPostingCandidate posting =
                provider.search("데이터 엔지니어", 1).getFirst();

        assertThat(provider.fetchSourceText(posting))
                .contains("공공 통계 데이터의 수집·정제 파이프라인")
                .contains("Python과 SQL 활용 능력");
    }

    @Test
    void fetchSourceText_withUnknownFixture_throwsNotFoundError() {
        JobPostingCandidate unknownPosting = new JobPostingCandidate(
                "unknown-fixture",
                "가상 회사",
                "알 수 없는 직무",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> provider.fetchSourceText(unknownPosting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SYNTHETIC_JOB_POSTING_NOT_FOUND");
    }
}
