CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    user_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_account_status
        CHECK (user_status IN ('ACTIVE', 'DELETED'))
);

CREATE TABLE external_identity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_external_identity_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_external_identity_provider
        CHECK (provider IN ('GITHUB')),
    CONSTRAINT uk_external_identity_provider_user
        UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_external_identity_user_id
    ON external_identity (user_id);
