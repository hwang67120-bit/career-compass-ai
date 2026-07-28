CREATE TABLE user_document (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    original_text TEXT NOT NULL,
    analysis_text TEXT NOT NULL,
    document_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_document_type CHECK (document_type IN ('RESUME', 'PORTFOLIO')),
    CONSTRAINT ck_user_document_status CHECK (document_status IN ('REGISTERED'))
);

CREATE INDEX idx_user_document_user_created_at
    ON user_document (user_id, created_at DESC);