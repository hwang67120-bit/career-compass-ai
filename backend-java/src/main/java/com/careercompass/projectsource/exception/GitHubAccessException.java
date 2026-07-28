package com.careercompass.projectsource.exception;

public class GitHubAccessException extends RuntimeException {

    private final GitHubAccessFailure failure;

    public GitHubAccessException(GitHubAccessFailure failure) {
        this.failure = failure;
    }

    public GitHubAccessException(GitHubAccessFailure failure, Throwable cause) {
        super(cause);
        this.failure = failure;
    }

    public GitHubAccessFailure getFailure() {
        return failure;
    }
}
