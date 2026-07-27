package com.careercompass.projectsource.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class GitHubApiPropertiesTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    @Test
    void create_withOfficialApiHost_acceptsConfiguration() {
        assertThatCode(() -> createProperties("https://api.github.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void create_withLookalikeApiHost_rejectsConfiguration() {
        assertThatInvalidBaseUrl("https://api.github.com.evil.example");
    }

    @Test
    void create_withHttpApiHost_rejectsConfiguration() {
        assertThatInvalidBaseUrl("http://api.github.com");
    }

    @Test
    void create_withApiPath_rejectsConfiguration() {
        assertThatInvalidBaseUrl("https://api.github.com/repos");
    }

    @Test
    void create_withNonPositiveTimeout_rejectsConfiguration() {
        assertThatThrownBy(() -> new GitHubApiProperties(
                URI.create("https://api.github.com"),
                "2022-11-28",
                "test-client",
                Duration.ZERO,
                READ_TIMEOUT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private GitHubApiProperties createProperties(String baseUrl) {
        return new GitHubApiProperties(
                URI.create(baseUrl),
                "2022-11-28",
                "test-client",
                CONNECT_TIMEOUT,
                READ_TIMEOUT
        );
    }

    private void assertThatInvalidBaseUrl(String baseUrl) {
        assertThatThrownBy(() -> createProperties(baseUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
