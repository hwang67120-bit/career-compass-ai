ALTER TABLE project_responsibility_extraction_task
    DROP CONSTRAINT uk_project_responsibility_task_snapshot;

ALTER TABLE project_responsibility_extraction_task
    ADD COLUMN extraction_status VARCHAR(40) NOT NULL DEFAULT 'EXTRACTED',
    ADD COLUMN failure_code VARCHAR(80),
    ADD COLUMN model_provider VARCHAR(30),
    ADD COLUMN model_name VARCHAR(200),
    ADD CONSTRAINT ck_project_responsibility_extraction_status
        CHECK (extraction_status IN ('EXTRACTING','EXTRACTED','PARTIALLY_EXTRACTED','FAILED'));

ALTER TABLE project_responsibility_extraction_task
    ALTER COLUMN extraction_status DROP DEFAULT;

CREATE TABLE project_responsibility_failed_technology (
    extraction_task_id UUID NOT NULL
        REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    technology_tag_id UUID NOT NULL REFERENCES technology_tag (id),
    PRIMARY KEY (extraction_task_id, technology_tag_id)
);

ALTER TABLE project_responsibility_extraction_task
    ADD CONSTRAINT uk_project_responsibility_task_analysis_source
        UNIQUE (linked_job_analysis_id, project_source_id);

CREATE TABLE project_responsibility_snapshot_exclusion (
    id UUID PRIMARY KEY,
    extraction_task_id UUID NOT NULL
        REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    exclusion_reason VARCHAR(80) NOT NULL
);

CREATE INDEX idx_project_responsibility_snapshot_exclusion_task
    ON project_responsibility_snapshot_exclusion (extraction_task_id);

CREATE TABLE project_technology_finding (
    id UUID PRIMARY KEY,
    extraction_task_id UUID NOT NULL REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    technology_tag_id UUID NOT NULL REFERENCES technology_tag (id),
    finding_status VARCHAR(30) NOT NULL CHECK (finding_status IN ('FOUND','NEEDS_REVIEW')),
    UNIQUE (extraction_task_id, technology_tag_id)
);

CREATE TABLE project_technology_finding_evidence (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL REFERENCES project_technology_finding (id) ON DELETE CASCADE,
    evidence_id VARCHAR(100) NOT NULL,
    file_path TEXT NOT NULL,
    excerpt VARCHAR(2000) NOT NULL,
    UNIQUE (finding_id, evidence_id)
);

CREATE TABLE project_technology_suggestion (
    id UUID PRIMARY KEY,
    extraction_task_id UUID NOT NULL REFERENCES project_responsibility_extraction_task (id) ON DELETE CASCADE,
    technology_tag_id UUID NOT NULL REFERENCES technology_tag (id),
    decision_status VARCHAR(30) NOT NULL CHECK (decision_status IN ('PENDING','ADDED','IGNORED')),
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    UNIQUE (extraction_task_id, technology_tag_id)
);

CREATE TABLE project_technology_suggestion_evidence (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL REFERENCES project_technology_suggestion (id) ON DELETE CASCADE,
    evidence_id VARCHAR(100) NOT NULL,
    file_path TEXT NOT NULL,
    excerpt VARCHAR(2000) NOT NULL,
    UNIQUE (suggestion_id, evidence_id)
);
