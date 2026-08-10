package com.careercompass.jobsearch.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 개발 샘플 Provider는 dev 프로필과 명시적 설정이 모두 있을 때만 선택된다.
 * 운영·기본 프로필에서는 샘플 공고가 실제 공공취업정보 대신 선택되지 않아야 한다.
 */
class JobPostingProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevSampleJobPostingProvider.class);

    @Test
    void withNoPropertyConfigured_selectsNoProvider() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(JobPostingProvider.class);
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
