CREATE TABLE IF NOT EXISTS user_profiles (
    user_id VARCHAR(100) PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    updated_at BIGINT NOT NULL
);
