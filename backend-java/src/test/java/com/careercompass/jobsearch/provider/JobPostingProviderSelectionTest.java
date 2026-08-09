package com.careercompass.jobsearch.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.careercompass.jobsearch.client.Work24JobDetailFetcher;
import com.careercompass.jobsearch.client.Work24JobSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PR #48 코덱스 확인 사항 검증: 기본값은 Provider 미설정 상태이고, work24·dev-sample
 * 모두 명시적으로 설정해야만 선택된다. dev-sample은 dev 프로필이 아니면 절대 만들어지지
 * 않는다(운영·기본 프로필에서 개인정보 미검증 Work24가 우연히 기본으로 켜지지 않게 한다).
 */
class JobPostingProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Work24JobPostingProvider.class, DevSampleJobPostingProvider.class)
            .withBean(Work24JobSearchClient.class, () -> mock(Work24JobSearchClient.class))
            .withBean(Work24JobDetailFetcher.class, () -> mock(Work24JobDetailFetcher.class));

    @Test
    void withNoPropertyConfigured_selectsNoProvider() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(JobPostingProvider.class);
            assertThat(context).doesNotHaveBean(Work24JobPostingProvider.class);
            assertThat(context).doesNotHaveBean(DevSampleJobPostingProvider.class);
        });
    }

    @Test
    void withWork24PropertyExplicit_selectsWork24Provider() {
        contextRunner
                .withPropertyValues("job-search.provider=work24")
                .run(context -> {
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
