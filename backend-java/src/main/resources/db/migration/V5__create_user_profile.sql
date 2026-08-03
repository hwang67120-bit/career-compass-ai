CREATE TABLE user_profile (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    current_version INTEGER NOT NULL,
    lock_version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_profile_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_profile_user UNIQUE (user_id),
    CONSTRAINT ck_user_profile_current_version CHECK (current_version > 0),
    CONSTRAINT ck_user_profile_lock_version CHECK (lock_version >= 0)
);

CREATE TABLE user_profile_version (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL,
    profile_version INTEGER NOT NULL,
    target_job_title VARCHAR(100) NOT NULL,
    content_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_profile_version_profile
        FOREIGN KEY (user_profile_id) REFERENCES user_profile (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_profile_version_number
        UNIQUE (user_profile_id, profile_version),
    CONSTRAINT ck_user_profile_version_number CHECK (profile_version > 0)
);

CREATE TABLE user_profile_technology_tag (
    id UUID PRIMARY KEY,
    user_profile_version_id UUID NOT NULL,
    technology_tag_id UUID,
    raw_name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT fk_user_profile_tag_version
        FOREIGN KEY (user_profile_version_id)
        REFERENCES user_profile_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_tag_standard
        FOREIGN KEY (technology_tag_id) REFERENCES technology_tag (id),
    CONSTRAINT uk_user_profile_tag_order
        UNIQUE (user_profile_version_id, display_order),
    CONSTRAINT ck_user_profile_tag_source
        CHECK (source_type IN ('USER_SELECTED', 'USER_CUSTOM')),
    CONSTRAINT ck_user_profile_tag_selected_reference
        CHECK (source_type <> 'USER_SELECTED' OR technology_tag_id IS NOT NULL),
    CONSTRAINT ck_user_profile_tag_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_user_profile_version_profile
    ON user_profile_version (user_profile_id, profile_version DESC);

CREATE INDEX idx_user_profile_tag_version
    ON user_profile_technology_tag (user_profile_version_id, display_order);

CREATE INDEX idx_user_profile_tag_standard
    ON user_profile_technology_tag (technology_tag_id)
    WHERE technology_tag_id IS NOT NULL;
