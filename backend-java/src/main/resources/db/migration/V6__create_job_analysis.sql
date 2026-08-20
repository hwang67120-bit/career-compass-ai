CREATE TABLE job_analysis (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    user_profile_id UUID NOT NULL,
    user_profile_version INTEGER NOT NULL,
    analysis_status VARCHAR(30) NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    completed_units INTEGER NOT NULL,
    total_units INTEGER NOT NULL,
    lock_version BIGINT NOT NULL,
    queued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_job_analysis_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_analysis_profile_version
        FOREIGN KEY (user_profile_id, user_profile_version)
        REFERENCES user_profile_version (user_profile_id, profile_version),
    CONSTRAINT ck_job_analysis_status CHECK (
        analysis_status IN (
            'QUEUED',
            'RUNNING',
            'CANCELLATION_REQUESTED',
            'PARTIALLY_COMPLETED',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_job_analysis_step CHECK (
        current_step IN (
            'VALIDATING_INPUTS',
            'ANALYZING_REPOSITORIES',
            'GENERATING_SEARCH_PLAN',
            'SEARCHING_JOB_POSTINGS',
            'EXTRACTING_JOB_POSTINGS',
            'COMPARING_EVIDENCE',
            'FINALIZING_RESULT',
            'FINISHED'
        )
    ),
    CONSTRAINT ck_job_analysis_completed_units CHECK (completed_units >= 0),
    CONSTRAINT ck_job_analysis_total_units CHECK (total_units >= 0),
    CONSTRAINT ck_job_analysis_progress CHECK (completed_units <= total_units),
    CONSTRAINT ck_job_analysis_lock_version CHECK (lock_version >= 0)
);

CREATE TABLE job_analysis_project_source (
    job_analysis_id UUID NOT NULL,
    project_source_id UUID NOT NULL,
    selection_order INTEGER NOT NULL,
    CONSTRAINT pk_job_analysis_project_source
        PRIMARY KEY (job_analysis_id, project_source_id),
    CONSTRAINT fk_job_analysis_project_source_analysis
        FOREIGN KEY (job_analysis_id) REFERENCES job_analysis (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_analysis_project_source_source
        FOREIGN KEY (project_source_id) REFERENCES project_source (id),
    CONSTRAINT uk_job_analysis_project_source_order
        UNIQUE (job_analysis_id, selection_order),
    CONSTRAINT ck_job_analysis_project_source_order CHECK (selection_order >= 0)
);

CREATE INDEX idx_job_analysis_queue
    ON job_analysis (analysis_status, queued_at, id);

CREATE INDEX idx_job_analysis_user_created
    ON job_analysis (user_id, queued_at DESC);

CREATE INDEX idx_job_analysis_project_source_source
    ON job_analysis_project_source (project_source_id);
