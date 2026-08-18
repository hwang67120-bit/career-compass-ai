package com.careercompass.projectsource.exception;

public class RepositorySnapshotException extends RuntimeException {

    private final RepositorySnapshotFailure failure;

    public RepositorySnapshotException(RepositorySnapshotFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public RepositorySnapshotException(
            RepositorySnapshotFailure failure,
            Throwable cause
    ) {
        super(failure.name(), cause);
        this.failure = failure;
    }

    public RepositorySnapshotFailure getFailure() {
        return failure;
    }
}
