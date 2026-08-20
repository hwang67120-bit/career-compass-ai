ALTER TABLE job_analysis DROP CONSTRAINT ck_job_analysis_status;
ALTER TABLE job_analysis ADD CONSTRAINT ck_job_analysis_status CHECK (
    analysis_status IN ('QUEUED','RUNNING','AWAITING_USER_CONFIRMATION',
        'CANCELLATION_REQUESTED','PARTIALLY_COMPLETED','COMPLETED','FAILED','CANCELLED')
);

CREATE TABLE project_responsibility_extraction_task (
    id UUID PRIMARY KEY,
    project_source_id UUID NOT NULL REFERENCES project_source (id),
    linked_job_analysis_id UUID REFERENCES job_analysis (id) ON DELETE CASCADE,
    base_user_profile_version_id UUID NOT NULL REFERENCES user_profile_version (id),
    repository_version VARCHAR(40) NOT NULL,
    review_status VARCHAR(40) NOT NULL,
    lock_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_project_responsibility_review_status
        CHECK (review_status IN ('AWAITING_USER_CONFIRMATION','REVIEW_COMPLETED')),
    CONSTRAINT uk_project_responsibility_task_snapshot
        UNIQUE (project_source_id, repository_version, base_user_profile_version_id)
);

CREATE TABLE project_responsibility_candidate (
    id UUID PRIMARY KEY,
    extraction_task_id UUID NOT NULL REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    extracted_text VARCHAR(500),
    confirmed_text VARCHAR(500),
    candidate_status VARCHAR(30) NOT NULL,
    lock_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_project_responsibility_candidate_status
        CHECK (candidate_status IN ('UNCONFIRMED','CONFIRMED','REJECTED')),
    CONSTRAINT ck_project_responsibility_candidate_text
        CHECK ((candidate_status <> 'REJECTED') OR (extracted_text IS NULL AND confirmed_text IS NULL))
);

CREATE TABLE project_responsibility_task_technology (
    extraction_task_id UUID NOT NULL REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    technology_tag_id UUID NOT NULL REFERENCES technology_tag (id),
    PRIMARY KEY (extraction_task_id, technology_tag_id)
);

CREATE TABLE project_responsibility_candidate_technology (
    candidate_id UUID NOT NULL REFERENCES project_responsibility_candidate (id) ON DELETE CASCADE,
    technology_tag_id UUID NOT NULL REFERENCES technology_tag (id),
    PRIMARY KEY (candidate_id, technology_tag_id)
);

CREATE TABLE project_responsibility_evidence (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES project_responsibility_candidate (id) ON DELETE CASCADE,
    evidence_id VARCHAR(100) NOT NULL,
    file_path TEXT NOT NULL,
    excerpt VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_project_responsibility_evidence UNIQUE (candidate_id, evidence_id)
);

CREATE TABLE user_profile_project_responsibility (
    id UUID PRIMARY KEY,
    user_profile_version_id UUID NOT NULL REFERENCES user_profile_version (id) ON DELETE CASCADE,
    source_candidate_id UUID NOT NULL REFERENCES project_responsibility_candidate (id),
    project_source_id UUID NOT NULL REFERENCES project_source (id),
    confirmed_text VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT uk_user_profile_project_responsibility_candidate UNIQUE (user_profile_version_id, source_candidate_id),
    CONSTRAINT uk_user_profile_project_responsibility_order UNIQUE (user_profile_version_id, display_order),
    CONSTRAINT ck_user_profile_project_responsibility_order CHECK (display_order >= 0)
);

CREATE INDEX idx_project_responsibility_task_analysis
    ON project_responsibility_extraction_task (linked_job_analysis_id);
CREATE INDEX idx_project_responsibility_candidate_task
    ON project_responsibility_candidate (extraction_task_id, candidate_status);
CREATE INDEX idx_user_profile_project_responsibility_version
    ON user_profile_project_responsibility (user_profile_version_id, display_order);
