package com.careercompass.jobsearch.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.careercompass.jobsearch.client.Work24JobDetailFetcher;
import com.careercompass.jobsearch.client.Work24JobSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PR #49(docs/claude-dev-sample-provider-handoff.md) 확정 원칙 1·2번 검증: 개발용
 * 샘플 Provider는 dev 프로필과 job-search.provider=dev-sample이 모두 있어야만 선택되고,
 * 운영·기본 프로필에서는 절대 만들어지지 않는다.
 */
class JobPostingProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Work24JobPostingProvider.class, DevSampleJobPostingProvider.class)
            .withBean(Work24JobSearchClient.class, () -> mock(Work24JobSearchClient.class))
            .withBean(Work24JobDetailFetcher.class, () -> mock(Work24JobDetailFetcher.class));

    @Test
    void withDefaultConfig_selectsWork24Provider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JobPostingProvider.class);
            assertThat(context).hasSingleBean(Work24JobPostingProvider.class);
            assertThat(context).doesNotHaveBean(DevSampleJobPostingProvider.class);
        });
    }

    @Test
    void withDevProfileAndDevSampleProperty_selectsDevSampleProvider() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev", "job-search.provider=dev-sample")
                .run(context -> {
                    assertThat(context).hasSingleBean(JobPostingProvider.class);
                    assertThat(context).hasSingleBean(DevSampleJobPostingProvider.class);
                    assertThat(context).doesNotHaveBean(Work24JobPostingProvider.class);
                });
    }

    @Test
    void withDevSamplePropertyButDefaultProfile_selectsNoProvider() {
        contextRunner
                .withPropertyValues("job-search.provider=dev-sample")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JobPostingProvider.class);
                    assertThat(context).doesNotHaveBean(DevSampleJobPostingProvider.class);
                });
    }

    @Test
    void withDevSamplePropertyAndProdProfile_selectsNoProvider() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "job-search.provider=dev-sample")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JobPostingProvider.class);
                    assertThat(context).doesNotHaveBean(DevSampleJobPostingProvider.class);
                });
    }
}
