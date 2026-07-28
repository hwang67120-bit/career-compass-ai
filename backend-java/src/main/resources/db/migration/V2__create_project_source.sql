CREATE TABLE project_source (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    repository_url TEXT NOT NULL,
    repository_full_name TEXT NOT NULL,
    default_branch TEXT NOT NULL,
    commit_sha TEXT NOT NULL,
    project_source_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_project_source_status
        CHECK (project_source_status IN ('REGISTERED'))
);

CREATE INDEX idx_project_source_user_created_at
    ON project_source (user_id, created_at DESC);
