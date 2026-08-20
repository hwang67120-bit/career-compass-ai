CREATE TABLE job_analysis_posting (
    id UUID PRIMARY KEY,
    job_analysis_id UUID NOT NULL,
    provider_posting_id VARCHAR(100) NOT NULL,
    company_name VARCHAR(200),
    original_job_title VARCHAR(300),
    source_url VARCHAR(500),
    extraction TEXT NOT NULL,
    model_executions TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_job_analysis_posting_analysis
        FOREIGN KEY (job_analysis_id) REFERENCES job_analysis (id) ON DELETE CASCADE,
    CONSTRAINT uk_job_analysis_posting_provider
        UNIQUE (job_analysis_id, provider_posting_id)
);

CREATE INDEX idx_job_analysis_posting_analysis
    ON job_analysis_posting (job_analysis_id);
