package com.careercompass.projectsource.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubRestClientTest {

    private GitHubRepositoryCoordinates coordinates;
    private MockRestServiceServer server;
    private GitHubRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubRestClient(builder.build());
        coordinates = GitHubRepositoryCoordinates.createFromUrl(
                "https://github.com/octocat/Hello-World");
    }

    @Test
    void fetchRepository_withPublicRepository_returnsMetadata() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "full_name": "octocat/Hello-World",
                          "private": false,
                          "default_branch": "main",
                          "disabled": false
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubRepositoryMetadata metadata = client.fetchRepository(coordinates);

        assertThat(metadata.fullName()).isEqualTo("octocat/Hello-World");
        assertThat(metadata.privateRepository()).isFalse();
        assertThat(metadata.defaultBranch()).isEqualTo("main");
        server.verify();
    }

    @Test
    void fetchLatestCommitSha_withDefaultBranch_returnsCommitSha() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World/commits/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"sha":"7fd1a60b01f91b314f59955a4e4d92aa1d1f36f3"}
                        """, MediaType.APPLICATION_JSON));

        String commitSha = client.fetchLatestCommitSha(coordinates, "main");

        assertThat(commitSha)
                .isEqualTo("7fd1a60b01f91b314f59955a4e4d92aa1d1f36f3");
        server.verify();
    }

    @Test
    void fetchRepository_withNotFoundResponse_returnsUnavailableFailure() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World"))
                .andRespond(withResourceNotFound());

        assertFailure(GitHubAccessFailure.REPOSITORY_UNAVAILABLE);
    }

    @Test
    void fetchRepository_withRedirectResponse_rejectsRedirect() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World"))
                .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
                        .header("Location", "https://evil.example/repository"));

        assertFailure(GitHubAccessFailure.REDIRECTED);
    }

    @Test
    void fetchRepository_withRateLimitResponse_returnsRateLimitFailure() {
        server.expect(once(), requestTo(
                        "https://api.github.com/repos/octocat/Hello-World"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertFailure(GitHubAccessFailure.RATE_LIMITED);
    }

    private void assertFailure(GitHubAccessFailure expectedFailure) {
        assertThatThrownBy(() -> client.fetchRepository(coordinates))
                .isInstanceOfSatisfying(GitHubAccessException.class,
                        exception -> assertThat(exception.getFailure())
                                .isEqualTo(expectedFailure));
        server.verify();
    }
}
