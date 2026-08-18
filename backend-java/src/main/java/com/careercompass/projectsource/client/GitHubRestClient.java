package com.careercompass.projectsource.client;

import java.util.List;

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

    public GitHubRestClient(@Qualifier("gitHubApiRestClient") RestClient restClient) {
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

    @Override
    public GitHubRepositoryTree fetchTree(
            GitHubRepositoryCoordinates coordinates,
            String commitSha
    ) {
        try {
            GitHubTreeApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "repos", coordinates.owner(), coordinates.repository(),
                                    "git", "trees", commitSha)
                            .queryParam("recursive", "1")
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
                    .requiredBody(GitHubTreeApiResponse.class);
            if (response.tree() == null) {
                throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE);
            }
            List<GitHubRepositoryTree.Entry> entries = response.tree().stream()
                    .filter(entry -> "blob".equals(entry.type()))
                    .map(entry -> {
                        if (entry.path() == null || entry.sha() == null || entry.size() == null) {
                            throw new GitHubAccessException(
                                    GitHubAccessFailure.INVALID_RESPONSE);
                        }
                        return new GitHubRepositoryTree.Entry(
                                entry.path(), entry.type(), entry.sha(), entry.size());
                    })
                    .toList();
            return new GitHubRepositoryTree(entries, response.truncated());
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
    public GitHubRepositoryBlob fetchBlob(
            GitHubRepositoryCoordinates coordinates,
            String blobSha
    ) {
        try {
            GitHubBlobApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    "repos", coordinates.owner(), coordinates.repository(),
                                    "git", "blobs", blobSha)
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
                    .requiredBody(GitHubBlobApiResponse.class);
            if (response.content() == null || response.encoding() == null
                    || response.size() == null) {
                throw new GitHubAccessException(GitHubAccessFailure.INVALID_RESPONSE);
            }
            return new GitHubRepositoryBlob(
                    response.content(), response.encoding(), response.size());
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
    private record GitHubTreeApiResponse(
            List<GitHubTreeEntryApiResponse> tree,
            boolean truncated
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubTreeEntryApiResponse(
            String path,
            String type,
            String sha,
            Long size
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubBlobApiResponse(String content, String encoding, Long size) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubCommitApiResponse(String sha) {
    }
}
