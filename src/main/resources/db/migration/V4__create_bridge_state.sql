CREATE TABLE IF NOT EXISTS user_documents (
    uid VARCHAR(100) PRIMARY KEY,
    matches JSONB NOT NULL DEFAULT '{}'::jsonb,
    notifications JSONB NOT NULL DEFAULT '{}'::jsonb,
    given_ratings JSONB NOT NULL DEFAULT '{}'::jsonb,
    received_ratings JSONB NOT NULL DEFAULT '{}'::jsonb,
    rating_average DOUBLE PRECISION NOT NULL DEFAULT 0,
    rating_count INTEGER NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_documents (
    chat_id VARCHAR(255) PRIMARY KEY,
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,
    messages JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at BIGINT NOT NULL
);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS rating_average DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0;
