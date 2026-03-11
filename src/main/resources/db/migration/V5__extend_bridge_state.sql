ALTER TABLE user_documents
    ADD COLUMN IF NOT EXISTS collection JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS map_pins JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS nickname_registry (
    normalized_nickname VARCHAR(100) PRIMARY KEY,
    uid VARCHAR(100) NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_nickname_registry_uid ON nickname_registry(uid);
