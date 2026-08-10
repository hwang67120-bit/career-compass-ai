package com.careercompass.jobsearch.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.careercompass.jobsearch.client.PublicEmploymentJobSearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PublicEmploymentJobPostingProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PublicEmploymentJobPostingProvider.class)
            .withBean(PublicEmploymentJobSearchClient.class,
                    () -> mock(PublicEmploymentJobSearchClient.class));

    @Test
    void withPublicEmploymentProperty_selectsPublicEmploymentProvider() {
        contextRunner
                .withPropertyValues("job-search.provider=public-employment")
                .run(context -> {
                    assertThat(context).hasSingleBean(JobPostingProvider.class);
                    assertThat(context).hasSingleBean(PublicEmploymentJobPostingProvider.class);
                });
    }

    @Test
    void withDifferentProviderProperty_selectsNoPublicEmploymentProvider() {
        contextRunner
                .withPropertyValues("job-search.provider=dev-sample")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JobPostingProvider.class);
                    assertThat(context).doesNotHaveBean(PublicEmploymentJobPostingProvider.class);
                });
    }
}
