package com.careercompass.projectsource.client;

import com.careercompass.projectsource.domain.GitHubRepositoryCoordinates;
import com.careercompass.projectsource.exception.GitHubAccessException;
import com.careercompass.projectsource.exception.GitHubAccessFailure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubRestClient implements GitHubRepositoryGateway {

    private final RestClient restClient;

    public GitHubRestClient(@Qualifier("gitHubRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public GitHubRepositoryMetadata fetchRepository(GitHubRepositoryCoordinates coordinates) {
        try {
            GitHubRepositoryApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("repos", coordinates.owner(), coordinates.repository())
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(GitHubAccessFailure.REDIRECTED);
                            })
                    .onStatus(status -> status.value() == 404,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(
                                        GitHubAccessFailure.REPOSITORY_UNAVAILABLE);
                            })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(GitHubAccessFailure.RATE_LIMITED);
                            })
                    .onStatus(HttpStatusCode::isError,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(
                                        GitHubAccessFailure.SERVICE_UNAVAILABLE);
                            })
                    .requiredBody(GitHubRepositoryApiResponse.class);
            return new GitHubRepositoryMetadata(
                    response.fullName(),
                    response.privateRepository(),
                    response.defaultBranch(),
                    response.disabled()
            );
        } catch (GitHubAccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GitHubAccessException(
                    GitHubAccessFailure.SERVICE_UNAVAILABLE, exception);
        } catch (IllegalStateException exception) {
            throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE, exception);
        }
    }

    @Override
    public String fetchLatestCommitSha(
            GitHubRepositoryCoordinates coordinates,
            String defaultBranch
    ) {
        try {
            GitHubCommitApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "repos",
                                    coordinates.owner(),
                                    coordinates.repository(),
                                    "commits",
                                    defaultBranch
                            )
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(GitHubAccessFailure.REDIRECTED);
                            })
                    .onStatus(status -> status.value() == 404 || status.value() == 409,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(
                                        GitHubAccessFailure.REPOSITORY_UNAVAILABLE);
                            })
                    .onStatus(status -> status.value() == 403 || status.value() == 429,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(GitHubAccessFailure.RATE_LIMITED);
                            })
                    .onStatus(HttpStatusCode::isError,
                            (request, serverResponse) -> {
                                throw new GitHubAccessException(
                                        GitHubAccessFailure.SERVICE_UNAVAILABLE);
                            })
                    .requiredBody(GitHubCommitApiResponse.class);
            return response.sha();
        } catch (GitHubAccessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GitHubAccessException(
                    GitHubAccessFailure.SERVICE_UNAVAILABLE, exception);
        } catch (IllegalStateException exception) {
            throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE, exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubRepositoryApiResponse(
            @JsonProperty("full_name") String fullName,
            @JsonProperty("private") boolean privateRepository,
            @JsonProperty("default_branch") String defaultBranch,
            boolean disabled
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitApiResponse(String sha) {
    }
}
