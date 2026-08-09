ALTER TABLE job_analysis_posting
    ALTER COLUMN job_posting_id SET NOT NULL,
    ALTER COLUMN extraction_task_id SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL;
