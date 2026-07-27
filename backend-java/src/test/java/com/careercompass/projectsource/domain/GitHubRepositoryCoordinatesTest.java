package com.careercompass.projectsource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import com.careercompass.projectsource.exception.InvalidGitHubRepositoryUrlException;
import org.junit.jupiter.api.Test;

class GitHubRepositoryCoordinatesTest {

    @Test
    void createFromUrl_withPublicRepositoryUrl_returnsCanonicalCoordinates() {
        GitHubRepositoryCoordinates coordinates =
                GitHubRepositoryCoordinates.createFromUrl(
                        "https://github.com/octocat/Hello-World");

        assertThat(coordinates.owner()).isEqualTo("octocat");
        assertThat(coordinates.repository()).isEqualTo("Hello-World");
        assertThat(coordinates.canonicalUrl())
                .isEqualTo(URI.create("https://github.com/octocat/Hello-World"));
    }

    @Test
    void createFromUrl_withGitSuffixAndTrailingSlash_normalizesRepositoryUrl() {
        GitHubRepositoryCoordinates coordinates =
                GitHubRepositoryCoordinates.createFromUrl(
                        "https://github.com/octocat/Hello-World.git/");

        assertThat(coordinates.repository()).isEqualTo("Hello-World");
        assertThat(coordinates.canonicalUrl())
                .isEqualTo(URI.create("https://github.com/octocat/Hello-World"));
    }

    @Test
    void createFromUrl_withLookalikeHost_rejectsUrl() {
        assertThatInvalid("https://github.com.evil.example/octocat/Hello-World");
    }

    @Test
    void createFromUrl_withUserInformationHostTrick_rejectsUrl() {
        assertThatInvalid("https://github.com@evil.example/octocat/Hello-World");
    }

    @Test
    void createFromUrl_withHttpScheme_rejectsUrl() {
        assertThatInvalid("http://github.com/octocat/Hello-World");
    }

    @Test
    void createFromUrl_withCustomPort_rejectsUrl() {
        assertThatInvalid("https://github.com:443/octocat/Hello-World");
    }

    @Test
    void createFromUrl_withRepositorySubpage_rejectsUrl() {
        assertThatInvalid("https://github.com/octocat/Hello-World/tree/main");
    }

    @Test
    void createFromUrl_withQueryOrFragment_rejectsUrl() {
        assertThatInvalid("https://github.com/octocat/Hello-World?tab=readme");
        assertThatInvalid("https://github.com/octocat/Hello-World#readme");
    }

    @Test
    void createFromUrl_withEncodedPath_rejectsUrl() {
        assertThatInvalid("https://github.com/octocat%2FHello-World");
    }

    @Test
    void createFromUrl_withMissingRepository_rejectsUrl() {
        assertThatInvalid("https://github.com/octocat");
    }

    @Test
    void createFromUrl_withBlankValue_rejectsUrl() {
        assertThatInvalid(" ");
        assertThatInvalid(null);
    }

    private void assertThatInvalid(String repositoryUrl) {
        assertThatThrownBy(() -> GitHubRepositoryCoordinates.createFromUrl(repositoryUrl))
                .isInstanceOf(InvalidGitHubRepositoryUrlException.class);
    }
}
