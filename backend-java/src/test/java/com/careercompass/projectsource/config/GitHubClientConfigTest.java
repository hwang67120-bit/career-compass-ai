package com.careercompass.projectsource.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import com.careercompass.projectsource.client.GitHubRepositoryGateway;
import com.careercompass.projectsource.client.GitHubRestClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class GitHubClientConfigTest {

    @Test
    void applicationContext_withGitHubClientBeans_startsWithoutBeanNameCollision() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(RestClient.Builder.class, () -> RestClient.builder());
            context.registerBean(
                    GitHubApiProperties.class,
                    () -> new GitHubApiProperties(
                            URI.create("https://api.github.com"),
                            "2022-11-28",
                            "career-compass-ai-test",
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(1)
                    )
            );
            context.register(GitHubClientConfig.class, GitHubRestClient.class);

            context.refresh();

            assertThat(context.containsBean("gitHubApiRestClient")).isTrue();
            assertThat(context.getBean(GitHubRepositoryGateway.class))
                    .isInstanceOf(GitHubRestClient.class);
        }
    }
}
